package io.github.ksshen0000.vda5050.conformance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.client.MasterClient;
import io.github.ksshen0000.vda5050.client.MasterListener;
import io.github.ksshen0000.vda5050.client.VehicleId;
import io.github.ksshen0000.vda5050.sim.Pose;
import io.github.ksshen0000.vda5050.sim.SimulatedAgv;
import io.github.ksshen0000.vda5050.sim.SimulationEngine;
import io.github.ksshen0000.vda5050.sim.SiteMap;
import io.github.ksshen0000.vda5050.sim.VehicleProfile;
import io.github.ksshen0000.vda5050.sim.VirtualVehicle;
import io.github.ksshen0000.vda5050.transport.Qos;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryBroker;
import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.BlockingType;
import io.github.ksshen0000.vda5050.v2.Edge;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.Node;
import io.github.ksshen0000.vda5050.v2.NodePosition;
import io.github.ksshen0000.vda5050.v2.Order;

/**
 * Simulated vehicles and a scripted fleet control on an in-memory broker, with an observer recording
 * every message into a trace. Time is simulated, so tests are fast and deterministic.
 */
final class SimulatedSite {

    static final String MANUFACTURER = "TESTKIT-SIM";
    static final String MAP = "SITE";

    private final InMemoryBroker broker = new InMemoryBroker();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-25T08:00:00Z"));
    private final SimulationEngine engine = new SimulationEngine(50, 1.0);
    private final MasterClient fleet;
    private final List<TraceEntry> trace = new ArrayList<>();

    SimulatedSite() {
        var observer = broker.newClient();
        observer.connect(null);
        observer.subscribe("#", Qos.AT_MOST_ONCE, (topic, payload) -> trace.add(new TraceEntry(clock.instant(), topic, payload, null)));
        fleet = new MasterClient(broker.newClient(), "uagv", clock);
        fleet.start(new MasterListener() {
        });
    }

    SimulatedSite vehicle(String serialNumber, double x, double y, String nodeId) {
        VehicleProfile profile = VehicleProfile.defaults();
        VirtualVehicle vehicle = new VirtualVehicle(serialNumber, profile, SiteMap.empty(MAP), new Pose(x, y, 0), nodeId);
        SimulatedAgv agv = new SimulatedAgv(vehicle,
                new AgvClient(broker.newClient(), "uagv", MANUFACTURER, serialNumber, clock), 10.0, 1.0);
        agv.connect();
        engine.add(agv);
        return this;
    }

    void send(String serialNumber, Order order) {
        fleet.sendOrder(new VehicleId(MANUFACTURER, serialNumber), order);
    }

    void instant(String serialNumber, Action... actions) {
        fleet.sendInstantActions(new VehicleId(MANUFACTURER, serialNumber), new InstantActions(Header.UNSTAMPED, List.of(actions)));
    }

    void run(double seconds) {
        double dt = 0.05;
        for (int i = 0; i < Math.round(seconds / dt); i++) {
            clock.advance(Duration.ofMillis(50));
            engine.step(dt);
        }
    }

    List<TraceEntry> trace() {
        return List.copyOf(trace);
    }

    static Action action(String type, String id) {
        return Action.of(type, id, BlockingType.HARD);
    }

    /** Straight line through the given points, 10 m apart on the x axis: {@code line("o1", 0, 0, "A", "B")}. */
    static OrderBuilder order(String orderId, long updateId) {
        return new OrderBuilder(orderId, updateId, 0);
    }

    static OrderBuilder update(String orderId, long updateId, long firstSequenceId) {
        return new OrderBuilder(orderId, updateId, firstSequenceId);
    }

    static final class OrderBuilder {

        private final String orderId;
        private final long updateId;
        private final List<Node> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private long sequenceId;
        private boolean released = true;

        OrderBuilder(String orderId, long updateId, long firstSequenceId) {
            this.orderId = orderId;
            this.updateId = updateId;
            this.sequenceId = firstSequenceId;
        }

        OrderBuilder horizon() {
            released = false;
            return this;
        }

        OrderBuilder node(String id, double x, double y, Action... actions) {
            if (!nodes.isEmpty()) {
                Node previous = nodes.getLast();
                edges.add(Edge.of(previous.nodeId() + "-" + id, sequenceId++, released, previous.nodeId(), id, null, null, null));
            }
            nodes.add(new Node(id, sequenceId++, null, released, NodePosition.of(x, y, null, MAP), List.of(actions)));
            return this;
        }

        Order build() {
            return new Order(Header.UNSTAMPED, orderId, updateId, nodes, edges);
        }
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
