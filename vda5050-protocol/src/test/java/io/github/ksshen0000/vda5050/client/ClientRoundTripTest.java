package io.github.ksshen0000.vda5050.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.transport.Qos;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryBroker;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryTransport;
import io.github.ksshen0000.vda5050.v2.Connection;
import io.github.ksshen0000.vda5050.v2.ConnectionState;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.SampleMessages;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.Vda5050Message;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;

class ClientRoundTripTest {

    private static final Instant NOW = Instant.parse("2026-10-18T08:00:00Z");
    private static final VehicleId AGV = new VehicleId("TESTKIT-SIM", "AGV-001");

    private final InMemoryBroker broker = new InMemoryBroker();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final List<Order> orders = new CopyOnWriteArrayList<>();
    private final List<InstantActions> instantActions = new CopyOnWriteArrayList<>();
    private final RecordingListener master = new RecordingListener();

    private InMemoryTransport agvTransport;
    private AgvClient agv;
    private MasterClient fleet;

    @BeforeEach
    void setUp() {
        fleet = new MasterClient(broker.newClient(), "uagv", clock);
        fleet.start(master);
        agvTransport = broker.newClient();
        agv = new AgvClient(agvTransport, "uagv", AGV.manufacturer(), AGV.serialNumber(), clock);
        agv.start(orders::add, instantActions::add);
    }

    @Test
    void vehicleAnnouncesOnlineRetained() {
        assertThat(master.connections).extracting(Connection::connectionState).containsExactly(ConnectionState.ONLINE);
        assertThat(broker.retainedMessage("uagv/v2/TESTKIT-SIM/AGV-001/connection")).isNotNull();
    }

    @Test
    void lateFleetControlStillLearnsTheConnectionState() {
        RecordingListener late = new RecordingListener();
        new MasterClient(broker.newClient(), "uagv", clock).start(late);

        assertThat(late.connections).extracting(Connection::connectionState).containsExactly(ConnectionState.ONLINE);
    }

    @Test
    void orderReachesTheVehicleWithAStampedHeader() {
        Order order = SampleMessages.order().withHeader(Header.UNSTAMPED);

        fleet.sendOrder(AGV, order);
        fleet.sendOrder(AGV, order);

        assertThat(orders).hasSize(2);
        assertThat(orders.getFirst().nodes()).isEqualTo(order.nodes());
        assertThat(orders).extracting(Order::headerId).containsExactly(0L, 1L);
        assertThat(orders.getFirst()).extracting(Order::manufacturer, Order::serialNumber, Order::version, Order::timestamp)
                .containsExactly("TESTKIT-SIM", "AGV-001", "2.1.0", NOW);
    }

    @Test
    void ordersForOtherVehiclesAreNotDelivered() {
        fleet.sendOrder(new VehicleId("TESTKIT-SIM", "AGV-002"), SampleMessages.order());

        assertThat(orders).isEmpty();
    }

    @Test
    void instantActionsReachTheVehicle() {
        fleet.sendInstantActions(AGV, SampleMessages.instantActions());

        assertThat(instantActions).singleElement()
                .satisfies(ia -> assertThat(ia.actions()).hasSize(2));
    }

    @Test
    void stateReachesFleetControlWithVehicleIdentity() {
        agv.publishState(SampleMessages.state());
        agv.publishState(SampleMessages.state());

        assertThat(master.states).hasSize(2);
        assertThat(master.vehicles).containsOnly(AGV);
        assertThat(master.states).extracting(State::headerId).containsExactly(0L, 1L);
    }

    @Test
    void cleanShutdownAnnouncesOffline() {
        agv.close();

        assertThat(master.connections).extracting(Connection::connectionState)
                .containsExactly(ConnectionState.ONLINE, ConnectionState.OFFLINE);
    }

    @Test
    void connectionLossTriggersBrokenWill() {
        agvTransport.simulateConnectionLoss();

        assertThat(master.connections).extracting(Connection::connectionState)
                .containsExactly(ConnectionState.ONLINE, ConnectionState.CONNECTIONBROKEN);
    }

    @Test
    void malformedMessagesAreDroppedWithoutBreakingTheSubscription() {
        InMemoryTransport rogue = broker.newClient();
        rogue.connect(null);
        rogue.publish("uagv/v2/TESTKIT-SIM/AGV-001/order", "{not json".getBytes(), Qos.AT_MOST_ONCE, false);

        fleet.sendOrder(AGV, SampleMessages.order());

        assertThat(orders).hasSize(1);
    }

    @Test
    void clearedRetainedStateIsNotRediscovered() {
        agv.close();
        fleet.clearRetained(AGV);

        RecordingListener late = new RecordingListener();
        new MasterClient(broker.newClient(), "uagv", clock).start(late);

        assertThat(broker.retainedMessage("uagv/v2/TESTKIT-SIM/AGV-001/connection")).isNull();
        assertThat(late.connections).isEmpty();
    }

    @Test
    void theTapSeesSentAndReceivedMessagesAsTheyTravel() {
        List<String> seen = new CopyOnWriteArrayList<>();
        MessageTap tap = new MessageTap() {
            @Override
            public void sent(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload) {
                seen.add("sent " + topic + " " + new String(payload, StandardCharsets.UTF_8).contains("\"headerId\":0"));
            }

            @Override
            public void received(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message, byte[] payload) {
                seen.add("received " + topic + " " + vehicle.serialNumber());
                throw new IllegalStateException("a failing tap must not lose the message");
            }
        };
        RecordingListener tapped = new RecordingListener();
        MasterClient client = new MasterClient(broker.newClient(), "uagv", clock, tap);
        client.start(tapped);

        client.sendOrder(AGV, SampleMessages.order().withHeader(Header.UNSTAMPED));
        agv.publishState(SampleMessages.state());

        assertThat(seen).containsExactly("received CONNECTION AGV-001", "sent ORDER true", "received STATE AGV-001");
        assertThat(tapped.states).hasSize(1);
    }

    private static final class RecordingListener implements MasterListener {

        final List<Connection> connections = new CopyOnWriteArrayList<>();
        final List<State> states = new CopyOnWriteArrayList<>();
        final List<VehicleId> vehicles = new CopyOnWriteArrayList<>();

        @Override
        public void onConnection(VehicleId vehicle, Connection connection) {
            connections.add(connection);
        }

        @Override
        public void onState(VehicleId vehicle, State state) {
            vehicles.add(vehicle);
            states.add(state);
        }
    }
}
