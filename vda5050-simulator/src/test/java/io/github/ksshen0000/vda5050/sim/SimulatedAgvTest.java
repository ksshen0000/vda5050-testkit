package io.github.ksshen0000.vda5050.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.client.MasterClient;
import io.github.ksshen0000.vda5050.client.MasterListener;
import io.github.ksshen0000.vda5050.client.VehicleId;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryBroker;
import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.ActionTypes;
import io.github.ksshen0000.vda5050.v2.BlockingType;
import io.github.ksshen0000.vda5050.v2.Connection;
import io.github.ksshen0000.vda5050.v2.ConnectionState;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.Vda5050Codec;
import io.github.ksshen0000.vda5050.v2.Vda5050SchemaValidator;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;
import io.github.ksshen0000.vda5050.v2.Visualization;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;

/** A simulated vehicle talking VDA 5050 to a fleet-control client over the in-memory broker. */
class SimulatedAgvTest {

    private static final VehicleId AGV = new VehicleId("TESTKIT-SIM", "AGV-1");

    private final InMemoryBroker broker = new InMemoryBroker();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-18T08:00:00Z"), ZoneOffset.UTC);
    private final List<State> states = new CopyOnWriteArrayList<>();
    private final List<Visualization> visualizations = new CopyOnWriteArrayList<>();
    private final List<Factsheet> factsheets = new CopyOnWriteArrayList<>();
    private final List<Connection> connections = new CopyOnWriteArrayList<>();
    private MasterClient fleet;
    private SimulationEngine engine;

    @BeforeEach
    void setUp() {
        fleet = new MasterClient(broker.newClient(), "uagv", clock);
        fleet.start(new MasterListener() {
            @Override
            public void onState(VehicleId vehicle, State state) {
                states.add(state);
            }

            @Override
            public void onVisualization(VehicleId vehicle, Visualization visualization) {
                visualizations.add(visualization);
            }

            @Override
            public void onFactsheet(VehicleId vehicle, Factsheet factsheet) {
                factsheets.add(factsheet);
            }

            @Override
            public void onConnection(VehicleId vehicle, Connection connection) {
                connections.add(connection);
            }
        });
        VirtualVehicle vehicle = new VirtualVehicle(AGV.serialNumber(), VirtualVehicleTest.PROFILE,
                SiteMap.empty(OrderBuilder.MAP), new Pose(0, 0, 0), "");
        SimulatedAgv agv = new SimulatedAgv(vehicle,
                new AgvClient(broker.newClient(), "uagv", AGV.manufacturer(), AGV.serialNumber(), clock), 1.0, 0.1);
        agv.connect();
        engine = new SimulationEngine(50, 1.0);
        engine.add(agv);
    }

    private void simulate(double seconds) {
        for (int i = 0; i < Math.round(seconds / 0.05); i++) {
            engine.step(0.05);
        }
    }

    @Test
    void announcesItselfWithConnectionAndFactsheet() {
        assertThat(connections).extracting(Connection::connectionState).containsExactly(ConnectionState.ONLINE);
        assertThat(factsheets).singleElement().satisfies(f ->
                assertThat(f.physicalParameters().speedMax()).isEqualTo(VirtualVehicleTest.PROFILE.maxSpeed()));
    }

    @Test
    void executesAnOrderReceivedOverMqtt() {
        fleet.sendOrder(AGV, OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());

        simulate(15);

        State last = states.getLast();
        assertThat(last.orderId()).isEqualTo("o1");
        assertThat(last.lastNodeId()).isEqualTo("B");
        assertThat(last.driving()).isFalse();
        assertThat(last.agvPosition().x()).isEqualTo(10.0);
    }

    @Test
    void publishesStateOnEventsAndOncePerSecondOtherwise() {
        simulate(0.05);
        states.clear();

        simulate(3);

        assertThat(states).hasSize(3);
        assertThat(states).extracting(State::headerId).isSorted();
    }

    @Test
    void publishesVisualizationAtTenHertz() {
        simulate(2);

        assertThat(visualizations).hasSizeBetween(19, 21);
    }

    @Test
    void everyPublishedStateIsSchemaValid() {
        Vda5050Codec codec = new Vda5050Codec();
        fleet.sendOrder(AGV, OrderBuilder.start("o1", 0).node("A", 0, 0)
                .node("B", 5, 0, Action.of(ActionTypes.PICK, "p", BlockingType.HARD)).node("C", 5, 5).build());
        fleet.sendInstantActions(AGV, new InstantActions(Header.UNSTAMPED,
                List.of(Action.of(ActionTypes.CANCEL_ORDER, "c-unused", BlockingType.HARD))));

        simulate(20);

        assertThat(states).isNotEmpty().allSatisfy(s ->
                assertThat(Vda5050SchemaValidator.errors(Vda5050Topic.STATE, codec.encode(s))).isEmpty());
    }

    @Test
    void factsheetRequestTriggersAFactsheet() {
        fleet.sendInstantActions(AGV, new InstantActions(Header.UNSTAMPED,
                List.of(Action.of(ActionTypes.FACTSHEET_REQUEST, "f1", BlockingType.NONE))));

        simulate(0.05);

        assertThat(factsheets).hasSize(2);
    }

    @Test
    void removingTheVehicleAnnouncesOffline() {
        engine.remove(AGV.serialNumber());

        assertThat(connections).extracting(Connection::connectionState)
                .containsExactly(ConnectionState.ONLINE, ConnectionState.OFFLINE);
    }

    @Test
    void droppedConnectionSilencesTheVehicleUntilItIsBack() {
        SimulatedAgv agv = engine.find(AGV.serialNumber()).orElseThrow();
        simulate(1);
        agv.dropConnection(3);
        states.clear();
        fleet.sendOrder(AGV, OrderBuilder.start("lost", 0).node("A", 0, 0).node("B", 5, 0).build());

        simulate(2.9);
        assertThat(states).isEmpty();
        assertThat(agv.isConnectionDropped()).isTrue();

        simulate(0.2);
        assertThat(states).isNotEmpty();
        // what was sent during the outage never arrived
        assertThat(states.getLast().orderId()).isNotEqualTo("lost");
    }
}
