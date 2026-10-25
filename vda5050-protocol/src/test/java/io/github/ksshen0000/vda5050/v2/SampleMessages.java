package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.github.ksshen0000.vda5050.v2.factsheet.ActionParameterSpec;
import io.github.ksshen0000.vda5050.v2.factsheet.AgvActionSpec;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;
import io.github.ksshen0000.vda5050.v2.factsheet.OptionalParameter;
import io.github.ksshen0000.vda5050.v2.factsheet.PhysicalParameters;
import io.github.ksshen0000.vda5050.v2.factsheet.ProtocolFeatures;
import io.github.ksshen0000.vda5050.v2.factsheet.ProtocolLimits;
import io.github.ksshen0000.vda5050.v2.factsheet.Timing;
import io.github.ksshen0000.vda5050.v2.factsheet.TypeSpecification;

/** Fully populated sample messages, used to check the model against the official schemas. */
public final class SampleMessages {

    public static final Header HEADER =
            new Header(7, Instant.parse("2026-10-18T08:00:00.123Z"), "2.1.0", "TESTKIT-SIM", "AGV-001");

    private SampleMessages() {
    }

    /** A -> B -> C where A-B is base and B-C horizon; the B-C edge is a cubic Bezier. */
    public static Order order() {
        Action lift = Action.of(ActionTypes.PICK, "act-1", BlockingType.HARD,
                new ActionParameter("stationType", "floor"),
                new ActionParameter("height", 0.05),
                new ActionParameter("confirm", true),
                new ActionParameter("extra", Map.of("lane", 2)));
        return new Order(HEADER, "order-1", 0, List.of(
                new Node("A", 0, null, true, NodePosition.of(0, 0, 0.0, "WH1"), List.of()),
                new Node("B", 2, "pick station", true, new NodePosition(10, 0, Math.PI / 2, 0.05, 0.1, "WH1", null),
                        List.of(lift)),
                new Node("C", 4, null, false, NodePosition.of(10, 10, null, "WH1"), List.of())),
                List.of(
                        Edge.of("AB", 1, true, "A", "B", 1.5, 10.0, null),
                        new Edge("BC", 3, null, false, "B", "C", 0.8, null, null, 0.0, "TANGENTIAL", null, false, 0.5,
                                10.4,
                                Trajectory.cubicBezier(new ControlPoint(10, 0, null), new ControlPoint(12, 3, null),
                                        new ControlPoint(12, 7, null), new ControlPoint(10, 10, null)),
                                new Corridor(0.5, 0.5, CorridorRefPoint.KINEMATICCENTER), List.of())));
    }

    public static InstantActions instantActions() {
        return new InstantActions(HEADER, List.of(
                Action.of(ActionTypes.START_PAUSE, "ia-1", BlockingType.HARD),
                Action.of(ActionTypes.INIT_POSITION, "ia-2", BlockingType.HARD,
                        new ActionParameter("x", 1.0), new ActionParameter("y", 2.0), new ActionParameter("theta", 0.0),
                        new ActionParameter("mapId", "WH1"), new ActionParameter("lastNodeId", "A"))));
    }

    public static State state() {
        return State.of(
                List.of(new StateMap("WH1", "3", null, MapStatus.ENABLED)),
                "order-1", 0, "A", 0, true, false, false, 2.5, OperatingMode.AUTOMATIC,
                List.of(new NodeState("B", 2, null, true, NodePosition.of(10, 0, null, "WH1"))),
                List.of(new EdgeState("AB", 1, null, true, null)),
                new AgvPosition(2.5, 0, 0, "WH1", null, true, 0.98, 0.02),
                new Velocity(1.0, 0.0, 0.0),
                List.of(new Load("PALLET-9", "EPAL", "front", new BoundingBoxReference(0, 0, 0.1, null),
                        new LoadDimensions(1.2, 0.8, 0.15), 120.0)),
                List.of(new ActionState("act-1", ActionTypes.PICK, null, ActionStatus.WAITING, null)),
                new BatteryState(87.5, 48.1, 98.0, false, 12000.0),
                List.of(new AgvError("orderUpdateError", List.of(new ErrorReference("orderId", "order-0")),
                        "stale update", null, ErrorLevel.WARNING)),
                SafetyState.CLEAR).withHeader(HEADER);
    }

    public static Visualization visualization() {
        return new Visualization(HEADER, AgvPosition.of(2.5, 0, 0, "WH1"), new Velocity(1.0, null, 0.0));
    }

    public static Connection connection() {
        return new Connection(HEADER, ConnectionState.ONLINE);
    }

    public static Factsheet factsheet() {
        return new Factsheet(0, null, null, null, null,
                new TypeSpecification("SIM-JACK", "simulated jacking AGV", AgvKinematic.DIFF, AgvClass.CARRIER, 500,
                        List.of(LocalizationType.NATURAL), List.of(NavigationType.VIRTUAL_LINE_GUIDED)),
                new PhysicalParameters(0.01, 1.5, 0.5, 0.8, null, 0.3, 0.8, 1.2),
                new ProtocolLimits(Map.of("idLen", 64), Map.of("order.nodes", 100, "order.edges", 99),
                        new Timing(0.5, 0.5, 1.0, 0.1)),
                new ProtocolFeatures(
                        List.of(new OptionalParameter("order.edges.trajectory", Support.SUPPORTED, null)),
                        List.of(new AgvActionSpec(ActionTypes.PICK, null, List.of(ActionScope.NODE),
                                List.of(new ActionParameterSpec("height", ValueDataType.FLOAT, null, true)), null,
                                List.of(BlockingType.HARD)))),
                Map.of(), Map.of(), null).withHeader(HEADER);
    }
}
