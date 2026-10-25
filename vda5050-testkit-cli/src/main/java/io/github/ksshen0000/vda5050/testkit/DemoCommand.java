package io.github.ksshen0000.vda5050.testkit;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.client.MasterClient;
import io.github.ksshen0000.vda5050.client.MasterListener;
import io.github.ksshen0000.vda5050.client.VehicleId;
import io.github.ksshen0000.vda5050.conformance.ConformanceChecker;
import io.github.ksshen0000.vda5050.conformance.TraceEntry;
import io.github.ksshen0000.vda5050.conformance.TraceFile;
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
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "demo", mixinStandardHelpOptions = true,
        description = {"Runs simulated vehicles and a scripted fleet control in memory and checks the traffic.",
                "No broker needed; time is simulated, so it takes about a second."})
final class DemoCommand implements Callable<Integer> {

    private static final String MANUFACTURER = "TESTKIT-SIM";
    private static final String MAP = "DEMO";

    @Option(names = "--with-mistakes", description = "Let fleet control send a broken order update, to see how it is reported.")
    boolean mistakes;

    @Option(names = {"-o", "--out"}, paramLabel = "FILE", description = "Also write the recorded trace to FILE.")
    Path out;

    @Mixin
    CheckSettings settings;

    private final InMemoryBroker broker = new InMemoryBroker();
    private final SimClock clock = new SimClock(Instant.parse("2026-01-01T08:00:00Z"));
    private final SimulationEngine engine = new SimulationEngine(50, 1.0);
    private final List<TraceEntry> trace = new ArrayList<>();
    private MasterClient fleet;

    @Override
    public Integer call() throws Exception {
        var observer = broker.newClient();
        observer.connect(null);
        observer.subscribe("#", Qos.AT_MOST_ONCE, (topic, payload) -> trace.add(new TraceEntry(clock.instant(), topic, payload, null)));
        fleet = new MasterClient(broker.newClient(), "uagv", clock);
        fleet.start(new MasterListener() {
        });
        vehicle("AGV-001", 0, 0);
        vehicle("AGV-002", 0, 10);
        run(1);

        send("AGV-001", order("o-1", 0, 0, true, "A", 0, 0, "B", 10, 0).horizon("C", 20, 0));
        run(3);
        if (mistakes) {
            // Wrong: an update has to start at the decision point B (sequenceId 2), not back at A
            send("AGV-001", order("o-1", 1, 0, true, "A", 0, 0, "B", 10, 0, "C", 20, 0));
            run(1);
        }
        send("AGV-001", order("o-1", mistakes ? 2 : 1, 2, true, "B", 10, 0, "C", 20, 0));
        run(3);
        instant("AGV-001", "startPause", "pause-1");
        run(2);
        instant("AGV-001", "stopPause", "resume-1");

        send("AGV-002", order("o-2", 0, 0, true, "P", 0, 10, "Q", 30, 10));
        run(5);
        instant("AGV-002", "cancelOrder", "cancel-1");
        run(30);
        instant("AGV-002", "cancelOrder", "cancel-2");
        run(5);

        if (out != null) {
            try (BufferedWriter writer = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
                TraceFile.write(trace, writer);
            }
            System.err.printf("%d messages written to %s%n", trace.size(), out);
        }
        return settings.publish(ConformanceChecker.check(trace, settings.options()), System.out);
    }

    private void vehicle(String serial, double x, double y) {
        VirtualVehicle vehicle = new VirtualVehicle(serial, VehicleProfile.defaults(), SiteMap.empty(MAP), new Pose(x, y, 0), "");
        SimulatedAgv agv = new SimulatedAgv(vehicle, new AgvClient(broker.newClient(), "uagv", MANUFACTURER, serial, clock), 10, 1);
        agv.connect();
        engine.add(agv);
    }

    private void run(double seconds) {
        for (int i = 0; i < Math.round(seconds / 0.05); i++) {
            clock.advance(Duration.ofMillis(50));
            engine.step(0.05);
        }
    }

    private void send(String serial, RouteBuilder route) {
        fleet.sendOrder(new VehicleId(MANUFACTURER, serial), route.build());
    }

    private void instant(String serial, String type, String id) {
        fleet.sendInstantActions(new VehicleId(MANUFACTURER, serial),
                new InstantActions(Header.UNSTAMPED, List.of(Action.of(type, id, BlockingType.HARD))));
    }

    /** {@code nodes} are triples of id, x, y. */
    private static RouteBuilder order(String orderId, long updateId, long firstSequenceId, boolean released, Object... nodes) {
        RouteBuilder route = new RouteBuilder(orderId, updateId, firstSequenceId);
        for (int i = 0; i < nodes.length; i += 3) {
            route.node((String) nodes[i], ((Number) nodes[i + 1]).doubleValue(), ((Number) nodes[i + 2]).doubleValue(), released);
        }
        return route;
    }

    private static final class RouteBuilder {

        private final String orderId;
        private final long updateId;
        private final List<Node> nodes = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private long sequenceId;

        RouteBuilder(String orderId, long updateId, long firstSequenceId) {
            this.orderId = orderId;
            this.updateId = updateId;
            this.sequenceId = firstSequenceId;
        }

        RouteBuilder horizon(String id, double x, double y) {
            return node(id, x, y, false);
        }

        RouteBuilder node(String id, double x, double y, boolean released) {
            if (!nodes.isEmpty()) {
                Node previous = nodes.getLast();
                edges.add(Edge.of(previous.nodeId() + "-" + id, sequenceId++, released, previous.nodeId(), id, null, null, null));
            }
            List<Action> actions = id.equals("B") ? List.of(Action.of("pick", "pick-" + orderId, BlockingType.HARD)) : List.of();
            nodes.add(new Node(id, sequenceId++, null, released, NodePosition.of(x, y, null, MAP), actions));
            return this;
        }

        Order build() {
            return new Order(Header.UNSTAMPED, orderId, updateId, nodes, edges);
        }
    }

    /** Simulated time, so the demo runs in a second and gives the same result every time. */
    private static final class SimClock extends Clock {

        private Instant now;

        SimClock(Instant start) {
            now = start;
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
