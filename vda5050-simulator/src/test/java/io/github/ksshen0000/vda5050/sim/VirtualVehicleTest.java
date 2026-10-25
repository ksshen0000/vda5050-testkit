package io.github.ksshen0000.vda5050.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.ActionParameter;
import io.github.ksshen0000.vda5050.v2.ActionState;
import io.github.ksshen0000.vda5050.v2.ActionStatus;
import io.github.ksshen0000.vda5050.v2.ActionTypes;
import io.github.ksshen0000.vda5050.v2.AgvError;
import io.github.ksshen0000.vda5050.v2.BlockingType;
import io.github.ksshen0000.vda5050.v2.ControlPoint;
import io.github.ksshen0000.vda5050.v2.ErrorTypes;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.NodePosition;
import io.github.ksshen0000.vda5050.v2.NodeState;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.Trajectory;
import io.github.ksshen0000.vda5050.v2.Vda5050Codec;
import io.github.ksshen0000.vda5050.v2.Vda5050SchemaValidator;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;

class VirtualVehicleTest {

    static final double DT = 0.05;

    /** 1 m/s, 0.5 m/s² both ways, quarter turn per second, 2 s pick/drop, 1 s otherwise. */
    static final VehicleProfile PROFILE = new VehicleProfile(1.0, 0.5, 0.5, Math.PI / 2, 0.1, 0.01, 0.0, 1.0, 1.0,
            Map.of(ActionTypes.PICK, 2.0, ActionTypes.DROP, 2.0), Set.of("lift"));

    private final Vda5050Codec codec = new Vda5050Codec();
    private double time;

    private VirtualVehicle vehicleAt(double x, double y) {
        return new VirtualVehicle("AGV-1", PROFILE, SiteMap.empty(OrderBuilder.MAP), new Pose(x, y, 0), "");
    }

    /** Steps until {@code condition} holds or {@code maxSeconds} pass; returns whether it held. */
    private boolean runUntil(VirtualVehicle v, double maxSeconds, BooleanSupplier condition) {
        double end = time + maxSeconds;
        while (time < end - 1e-9) {
            if (condition.getAsBoolean()) {
                return true;
            }
            v.step(DT);
            time += DT;
        }
        return condition.getAsBoolean();
    }

    private void run(VirtualVehicle v, double seconds) {
        runUntil(v, seconds, () -> false);
    }

    private static boolean arrivedAt(VirtualVehicle v, String node) {
        return v.lastNodeId().equals(node) && !v.isDriving();
    }

    private static List<String> errorTypes(VirtualVehicle v) {
        return v.state().errors().stream().map(AgvError::errorType).toList();
    }

    private static ActionStatus statusOf(VirtualVehicle v, String actionId) {
        return v.state().actionStates().stream().filter(a -> a.actionId().equals(actionId)).findFirst()
                .map(ActionState::actionStatus).orElseThrow();
    }

    private static InstantActions instant(Action... actions) {
        return new InstantActions(Header.UNSTAMPED, List.of(actions));
    }

    private void assertSchemaValid(VirtualVehicle v) {
        assertThat(Vda5050SchemaValidator.errors(Vda5050Topic.STATE, codec.encode(v.state().withHeader(
                new Header(0, java.time.Instant.EPOCH, "2.1.0", "M", "AGV-1"))))).isEmpty();
    }

    @Nested
    class Driving {

        @Test
        void followsTrapezoidalProfileAndStopsAtTheEnd() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).node("C", 20, 0).build());

            // 2 s accelerating (1 m), 18 s cruising (18 m), 2 s braking (1 m): 22 s in total
            assertThat(runUntil(v, 21.5, () -> arrivedAt(v, "C"))).isFalse();
            assertThat(runUntil(v, 1.5, () -> arrivedAt(v, "C"))).isTrue();
            assertThat(v.pose().x()).isCloseTo(20, within(1e-9));
            assertThat(v.state().nodeStates()).isEmpty();
            assertThat(v.state().edgeStates()).isEmpty();
            assertSchemaValid(v);
        }

        @Test
        void passesThroughIntermediateNodesWithoutStopping() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).node("C", 20, 0).build());

            run(v, 5);
            double minSpeed = Double.MAX_VALUE;
            while (time < 17) {
                v.step(DT);
                time += DT;
                minSpeed = Math.min(minSpeed, v.velocity());
            }

            assertThat(v.lastNodeId()).isEqualTo("B");
            assertThat(minSpeed).isGreaterThan(0.99);
        }

        @Test
        void reportsTraversedNodesAndRemainingStates() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).node("C", 20, 0).build());

            State atStart = v.state();
            assertThat(atStart.lastNodeId()).isEqualTo("A");
            assertThat(atStart.nodeStates()).extracting(NodeState::nodeId).containsExactly("B", "C");

            runUntil(v, 30, () -> v.lastNodeId().equals("B"));
            State afterB = v.state();
            assertThat(afterB.lastNodeSequenceId()).isEqualTo(2);
            assertThat(afterB.nodeStates()).extracting(NodeState::nodeId).containsExactly("C");
            assertThat(afterB.edgeStates()).singleElement().satisfies(e -> assertThat(e.edgeId()).isEqualTo("BC"));
            assertThat(afterB.distanceSinceLastNode()).isLessThan(0.1);
            assertSchemaValid(v);
        }

        @Test
        void respectsEdgeSpeedLimit() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).maxSpeed(0.3).node("B", 10, 0).build());

            run(v, 10);

            assertThat(v.velocity()).isCloseTo(0.3, within(1e-9));
        }

        @Test
        void stopsAtTheDecisionPointAndNeverEntersTheHorizon() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).horizon().node("C", 20, 0).build());

            assertThat(runUntil(v, 20, () -> arrivedAt(v, "B"))).isTrue();
            run(v, 10);

            assertThat(v.pose().x()).isCloseTo(10, within(1e-9));
            assertThat(v.state().nodeStates()).singleElement()
                    .satisfies(n -> assertThat(n).extracting(NodeState::nodeId, NodeState::released).containsExactly("C", false));
        }

        @Test
        void rotatesOnTheSpotAtASharpCorner() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).node("D", 10, 10).build());

            runUntil(v, 30, () -> v.lastNodeId().equals("B"));
            assertThat(v.velocity()).isZero();
            runUntil(v, 0.5, () -> false);

            assertThat(v.pose().x()).isCloseTo(10, within(1e-9));
            assertThat(v.pose().theta()).isBetween(0.1, Math.PI / 2 - 0.1);
            assertThat(v.isDriving()).isTrue();
            assertThat(runUntil(v, 30, () -> arrivedAt(v, "D"))).isTrue();
            assertThat(v.pose().theta()).isCloseTo(Math.PI / 2, within(1e-6));
        }

        @Test
        void rotatesToTheFinalNodeOrientation() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).nodeWithTheta("B", 5, 0, Math.PI).build());

            assertThat(runUntil(v, 30, () -> v.lastNodeId().equals("B") && !v.isDriving()
                    && Math.abs(Math.abs(v.pose().theta()) - Math.PI) < 1e-6)).isTrue();
        }

        @Test
        void followsABezierTrajectory() {
            VirtualVehicle v = vehicleAt(0, 0);
            Trajectory curve = Trajectory.cubicBezier(new ControlPoint(0, 0, null), new ControlPoint(5, 0, null),
                    new ControlPoint(10, 5, null), new ControlPoint(10, 10, null));
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).trajectory(curve).node("B", 10, 10).build());

            run(v, 8);
            assertThat(v.pose().y()).isGreaterThan(0.1);
            assertThat(v.pose().x() - v.pose().y()).isGreaterThan(0.5);

            assertThat(runUntil(v, 30, () -> arrivedAt(v, "B"))).isTrue();
            assertThat(v.pose().theta()).isCloseTo(Math.PI / 2, within(0.05));
        }

        @Test
        void resolvesPositionsAndCurvesFromTheMap() {
            SiteMap map = new SiteMap("TEST", "3",
                    Map.of("A", new SiteMap.Point(0, 0, "NORMAL"), "B", new SiteMap.Point(10, 10, "NORMAL")),
                    Map.of("AB", new SiteMap.Path("A", "B", List.of(new double[] {5, 0}, new double[] {10, 5}))));
            VirtualVehicle v = new VirtualVehicle("AGV-1", PROFILE, map, new Pose(0, 0, 0), "A");
            v.receiveOrder(OrderBuilder.start("o1", 0).mapNode("A").mapNode("B").build());

            assertThat(errorTypes(v)).isEmpty();
            run(v, 8);
            assertThat(v.pose().x() - v.pose().y()).isGreaterThan(0.5);
            assertThat(runUntil(v, 30, () -> arrivedAt(v, "B"))).isTrue();
            assertThat(v.state().maps()).singleElement().satisfies(m -> assertThat(m.mapVersion()).isEqualTo("3"));
        }
    }

    @Nested
    class OrderAcceptance {

        @Test
        void updateAtTheDecisionPointExtendsTheBaseWithoutStopping() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).horizon().node("C", 20, 0).build());
            run(v, 3);

            v.receiveOrder(OrderBuilder.update("o1", 1, 2).node("B", 10, 0).node("C", 20, 0).build());
            double minSpeed = Double.MAX_VALUE;
            while (time < 17) {
                v.step(DT);
                time += DT;
                minSpeed = Math.min(minSpeed, v.velocity());
            }

            assertThat(errorTypes(v)).isEmpty();
            assertThat(minSpeed).isGreaterThan(0.99);
            assertThat(runUntil(v, 10, () -> arrivedAt(v, "C"))).isTrue();
            assertThat(v.state().orderUpdateId()).isEqualTo(1);
        }

        @Test
        void updateAfterReachingTheDecisionPointContinuesTheOrder() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());
            runUntil(v, 30, () -> arrivedAt(v, "B"));

            v.receiveOrder(OrderBuilder.update("o1", 1, 2).node("B", 10, 0).node("C", 20, 0).build());

            assertThat(errorTypes(v)).isEmpty();
            assertThat(runUntil(v, 30, () -> arrivedAt(v, "C"))).isTrue();
        }

        @Test
        void updateNotStartingAtTheDecisionPointIsRejected() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());
            run(v, 2);

            v.receiveOrder(OrderBuilder.update("o1", 1, 0).node("A", 0, 0).node("C", 0, 10).build());

            assertThat(errorTypes(v)).containsExactly(ErrorTypes.ORDER_UPDATE_ERROR);
            assertThat(v.state().orderUpdateId()).isZero();
            assertThat(runUntil(v, 30, () -> arrivedAt(v, "B"))).isTrue();
        }

        @Test
        void deprecatedUpdateIsRejectedAndDuplicateIsIgnored() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 3).node("A", 0, 0).node("B", 10, 0).build());

            v.receiveOrder(OrderBuilder.start("o1", 3).node("A", 0, 0).node("B", 10, 0).build());
            assertThat(errorTypes(v)).isEmpty();

            v.receiveOrder(OrderBuilder.start("o1", 2).node("A", 0, 0).node("B", 10, 0).build());
            assertThat(errorTypes(v)).containsExactly(ErrorTypes.ORDER_UPDATE_ERROR);
        }

        @Test
        void newOrderWhileExecutingIsRejected() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());
            run(v, 1);

            v.receiveOrder(OrderBuilder.start("o2", 0).node("A", 0, 0).node("C", 0, 10).build());

            assertThat(errorTypes(v)).containsExactly(ErrorTypes.ORDER_UPDATE_ERROR);
            assertThat(v.orderId()).isEqualTo("o1");
        }

        @Test
        void newOrderAfterCompletionIsAccepted() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());
            runUntil(v, 30, () -> arrivedAt(v, "B"));

            v.receiveOrder(OrderBuilder.start("o2", 0).node("B", 10, 0).node("A", 0, 0).build());

            assertThat(v.orderId()).isEqualTo("o2");
            assertThat(runUntil(v, 40, () -> arrivedAt(v, "A"))).isTrue();
        }

        @Test
        void orderStartingAwayFromTheVehicleIsRejectedWithNoRoute() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveOrder(OrderBuilder.start("o1", 0).node("B", 10, 0).node("C", 20, 0).build());

            assertThat(errorTypes(v)).containsExactly(ErrorTypes.NO_ROUTE_ERROR);
            assertThat(v.orderId()).isEmpty();
        }

        @Test
        void allowedDeviationWidensTheStartTolerance() {
            VirtualVehicle v = vehicleAt(0.4, 0);

            v.receiveOrder(OrderBuilder.start("o1", 0)
                    .node("A", new NodePosition(0, 0, null, 0.5, null, OrderBuilder.MAP, null))
                    .node("B", 10, 0).build());

            assertThat(v.orderId()).isEqualTo("o1");
        }

        @Test
        void malformedOrderReportsValidationErrorUntilAnOrderIsAccepted() {
            VirtualVehicle v = vehicleAt(0, 0);
            var good = OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build();
            var bad = new io.github.ksshen0000.vda5050.v2.Order(Header.UNSTAMPED, "o0", 0, good.nodes(), List.of());

            v.receiveOrder(bad);
            run(v, 1);
            assertThat(errorTypes(v)).containsExactly(ErrorTypes.VALIDATION_ERROR);

            v.receiveOrder(good);
            assertThat(errorTypes(v)).isEmpty();
        }

        @Test
        void nodeOnAnotherMapIsAValidationError() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", NodePosition.of(0, 0, null, "OTHER")).build());

            assertThat(v.state().errors()).singleElement().satisfies(e -> {
                assertThat(e.errorType()).isEqualTo(ErrorTypes.VALIDATION_ERROR);
                assertThat(e.errorDescription()).contains("map OTHER");
            });
        }

        @Test
        void unsupportedActionTypeIsAnOrderError() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0)
                    .node("B", 10, 0, Action.of("teleport", "x1", BlockingType.HARD)).build());

            assertThat(v.state().errors()).singleElement().satisfies(e -> {
                assertThat(e.errorType()).isEqualTo(ErrorTypes.ORDER_ERROR);
                assertThat(e.errorDescription()).contains("teleport");
            });
            assertThat(v.orderId()).isEmpty();
        }

        @Test
        void vehicleWithoutPositionRejectsOrdersUntilInitialised() {
            VirtualVehicle v = new VirtualVehicle("AGV-1", PROFILE, SiteMap.empty("TEST"), null, "");
            var order = OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build();

            v.receiveOrder(order);
            assertThat(errorTypes(v)).containsExactly(ErrorTypes.NO_ROUTE_ERROR);
            assertThat(v.state().agvPosition().positionInitialized()).isFalse();

            v.receiveInstantActions(instant(Action.of(ActionTypes.INIT_POSITION, "i1", BlockingType.HARD,
                    new ActionParameter("x", 0.0), new ActionParameter("y", 0.0), new ActionParameter("theta", 0.0),
                    new ActionParameter("mapId", "TEST"), new ActionParameter("lastNodeId", "A"))));
            v.receiveOrder(order);

            assertThat(v.orderId()).isEqualTo("o1");
            assertThat(errorTypes(v)).isEmpty();
        }
    }

    @Nested
    class Actions {

        @Test
        void hardNodeActionStopsTheVehicleWhileRunning() {
            VirtualVehicle v = vehicleAt(0, 0);
            Action pick = Action.of(ActionTypes.PICK, "pick-1", BlockingType.HARD, new ActionParameter("loadId", "PALLET-7"));
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 5, 0, pick).node("C", 10, 0).build());

            assertThat(statusOf(v, "pick-1")).isEqualTo(ActionStatus.WAITING);
            runUntil(v, 30, () -> v.lastNodeId().equals("B"));
            run(v, 0.1);
            assertThat(statusOf(v, "pick-1")).isEqualTo(ActionStatus.RUNNING);
            assertThat(v.velocity()).isZero();

            run(v, 1.5);
            assertThat(v.pose().x()).isCloseTo(5, within(1e-9));
            assertThat(runUntil(v, 1, () -> statusOf(v, "pick-1") == ActionStatus.FINISHED)).isTrue();
            assertThat(v.loads()).singleElement().satisfies(l -> assertThat(l.loadId()).isEqualTo("PALLET-7"));

            assertThat(runUntil(v, 30, () -> arrivedAt(v, "C"))).isTrue();
            assertSchemaValid(v);
        }

        @Test
        void dropClearsTheLoad() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0)
                    .node("A", 0, 0, Action.of(ActionTypes.PICK, "p", BlockingType.HARD))
                    .node("B", 5, 0, Action.of(ActionTypes.DROP, "d", BlockingType.HARD)).build());

            runUntil(v, 5, () -> !v.loads().isEmpty());
            assertThat(v.loads()).hasSize(1);
            assertThat(runUntil(v, 30, () -> statusOf(v, "d") == ActionStatus.FINISHED)).isTrue();
            assertThat(v.loads()).isEmpty();
        }

        @Test
        void nonBlockingActionDoesNotStopTheVehicle() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0)
                    .node("B", 10, 0, Action.of("lift", "beep", BlockingType.NONE)).node("C", 20, 0).build());

            run(v, 5);
            double minSpeed = Double.MAX_VALUE;
            while (time < 17) {
                v.step(DT);
                time += DT;
                minSpeed = Math.min(minSpeed, v.velocity());
            }

            assertThat(minSpeed).isGreaterThan(0.99);
            assertThat(statusOf(v, "beep")).isEqualTo(ActionStatus.FINISHED);
        }

        @Test
        void hardActionsRunOneAfterAnother() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0,
                    Action.of("lift", "a1", BlockingType.HARD), Action.of("lift", "a2", BlockingType.HARD)).build());

            run(v, 0.5);
            assertThat(statusOf(v, "a1")).isEqualTo(ActionStatus.RUNNING);
            assertThat(statusOf(v, "a2")).isEqualTo(ActionStatus.WAITING);
            run(v, 1);
            assertThat(statusOf(v, "a1")).isEqualTo(ActionStatus.FINISHED);
            assertThat(statusOf(v, "a2")).isEqualTo(ActionStatus.RUNNING);
        }

        @Test
        void softActionsRunInParallel() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0,
                    Action.of("lift", "s1", BlockingType.SOFT), Action.of("lift", "s2", BlockingType.SOFT)).build());

            run(v, 0.5);

            assertThat(statusOf(v, "s1")).isEqualTo(ActionStatus.RUNNING);
            assertThat(statusOf(v, "s2")).isEqualTo(ActionStatus.RUNNING);
        }

        @Test
        void chargingOnlyWorksOnAChargingPoint() {
            SiteMap map = new SiteMap("TEST", "1",
                    Map.of("A", new SiteMap.Point(0, 0, "NORMAL"), "CH", new SiteMap.Point(5, 0, "CHARGING")), Map.of());
            VirtualVehicle v = new VirtualVehicle("AGV-1", PROFILE, map, new Pose(0, 0, 0), "A");
            v.setBattery(50);

            v.receiveInstantActions(instant(Action.of(ActionTypes.START_CHARGING, "c0", BlockingType.HARD)));
            run(v, 0.1);
            assertThat(statusOf(v, "c0")).isEqualTo(ActionStatus.FAILED);

            v.receiveOrder(OrderBuilder.start("o1", 0).mapNode("A")
                    .node("CH", null, Action.of(ActionTypes.START_CHARGING, "c1", BlockingType.HARD)).build());
            assertThat(runUntil(v, 30, () -> statusOf(v, "c1") == ActionStatus.FINISHED)).isTrue();
            assertThat(v.isCharging()).isTrue();
            double before = v.battery();
            run(v, 10);
            assertThat(v.battery()).isCloseTo(before + 10, within(0.01));
        }

        @Test
        void drivingDrainsTheBattery() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());

            runUntil(v, 30, () -> arrivedAt(v, "B"));

            assertThat(v.battery()).isCloseTo(100 - 10 * PROFILE.drainPerMeter(), within(1e-6));
        }

        @Test
        void emptyBatteryStopsTheVehicleWithAFatalError() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.setBattery(0.02);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());

            run(v, 20);

            assertThat(v.pose().x()).isLessThan(10);
            assertThat(v.state().errors()).anySatisfy(e -> assertThat(e.errorLevel())
                    .isEqualTo(io.github.ksshen0000.vda5050.v2.ErrorLevel.FATAL));
        }
    }

    @Nested
    class InstantActionsTest {

        @Test
        void pauseStopsTheVehicleAndResumeContinues() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 20, 0).build());
            run(v, 5);

            v.receiveInstantActions(instant(Action.of(ActionTypes.START_PAUSE, "p1", BlockingType.HARD)));
            run(v, 3);
            double x = v.pose().x();
            run(v, 3);

            assertThat(v.pose().x()).isEqualTo(x);
            assertThat(v.state().paused()).isTrue();
            assertThat(statusOf(v, "p1")).isEqualTo(ActionStatus.FINISHED);

            v.receiveInstantActions(instant(Action.of(ActionTypes.STOP_PAUSE, "p2", BlockingType.HARD)));
            assertThat(runUntil(v, 40, () -> arrivedAt(v, "B"))).isTrue();
        }

        @Test
        void cancelOrderStopsOnTheNextNodeAndFailsOutstandingActions() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0)
                    .node("C", 20, 0, Action.of(ActionTypes.PICK, "pick", BlockingType.HARD)).build());
            run(v, 5);
            assertThat(v.pose().x()).isBetween(1.0, 9.0);

            v.receiveInstantActions(instant(Action.of(ActionTypes.CANCEL_ORDER, "cancel", BlockingType.HARD)));
            assertThat(statusOf(v, "cancel")).isEqualTo(ActionStatus.RUNNING);
            assertThat(statusOf(v, "pick")).isEqualTo(ActionStatus.FAILED);

            assertThat(runUntil(v, 20, () -> statusOf(v, "cancel") == ActionStatus.FINISHED)).isTrue();
            assertThat(v.velocity()).isZero();
            assertThat(v.state().nodeStates()).isEmpty();
            assertThat(v.state().orderId()).isEqualTo("o1");
            assertThat(v.lastNodeId()).isEqualTo("B");
            assertThat(v.pose().x()).isEqualTo(10.0);

            // a new order starting from where the vehicle stands is accepted
            v.receiveOrder(OrderBuilder.start("o2", 0).node("B", 10, 0).node("A", 0, 0).build());
            assertThat(v.orderId()).isEqualTo("o2");
        }

        @Test
        void cancelWithoutOrderFailsWithWarning() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveInstantActions(instant(Action.of(ActionTypes.CANCEL_ORDER, "cancel", BlockingType.HARD)));

            assertThat(statusOf(v, "cancel")).isEqualTo(ActionStatus.FAILED);
            assertThat(v.state().errors()).singleElement().satisfies(e -> {
                assertThat(e.errorType()).isEqualTo("noOrderToCancel");
                assertThat(e.errorReferences()).anySatisfy(r -> assertThat(r.referenceValue()).isEqualTo("cancel"));
            });
        }

        @Test
        void initPositionIsRefusedDuringAnOrder() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 10, 0).build());

            v.receiveInstantActions(instant(Action.of(ActionTypes.INIT_POSITION, "i", BlockingType.HARD,
                    new ActionParameter("x", 5.0), new ActionParameter("y", 5.0))));

            assertThat(statusOf(v, "i")).isEqualTo(ActionStatus.FAILED);
            assertThat(v.pose().x()).isZero();
        }

        @Test
        void factsheetRequestIsSignalledOnce() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveInstantActions(instant(Action.of(ActionTypes.FACTSHEET_REQUEST, "f", BlockingType.NONE)));

            assertThat(v.takeFactsheetRequest()).isTrue();
            assertThat(v.takeFactsheetRequest()).isFalse();
        }

        @Test
        void unknownInstantActionFails() {
            VirtualVehicle v = vehicleAt(0, 0);

            v.receiveInstantActions(instant(Action.of("selfDestruct", "x", BlockingType.HARD)));

            assertThat(statusOf(v, "x")).isEqualTo(ActionStatus.FAILED);
            assertSchemaValid(v);
        }
    }

    @Nested
    class FaultInjectionTest {

        @Test
        void injectedWarningIsReportedUntilItRunsOutButDoesNotStopTheVehicle() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.injectError("laserWarning", io.github.ksshen0000.vda5050.v2.ErrorLevel.WARNING, "dirty scanner", 2);
            assertThat(v.takeStateEvent()).isTrue();
            assertThat(errorTypes(v)).containsExactly("laserWarning");
            assertSchemaValid(v);

            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 4, 0).build());
            run(v, 2.5);
            assertThat(v.pose().x()).isPositive();
            assertThat(errorTypes(v)).isEmpty();
            assertThat(v.takeStateEvent()).isTrue();
        }

        @Test
        void fatalErrorStopsTheVehicleUntilCleared() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 20, 0).build());
            run(v, 4);
            v.injectError("motorFault", io.github.ksshen0000.vda5050.v2.ErrorLevel.FATAL, null, 0);
            run(v, 4);
            double x = v.pose().x();
            run(v, 10);
            assertThat(v.pose().x()).isEqualTo(x);
            assertThat(v.faults()).containsExactly("motorFault");

            v.clearFaults();
            assertThat(runUntil(v, 40, () -> arrivedAt(v, "B"))).isTrue();
        }

        @Test
        void obstacleStopsTheVehicleForAWhile() {
            VirtualVehicle v = vehicleAt(0, 0);
            v.receiveOrder(OrderBuilder.start("o1", 0).node("A", 0, 0).node("B", 20, 0).build());
            run(v, 4);
            v.blockByObstacle(5);
            run(v, 3);
            double x = v.pose().x();
            run(v, 1);
            assertThat(v.pose().x()).isEqualTo(x);
            assertThat(errorTypes(v)).containsExactly(VirtualVehicle.OBSTACLE);

            assertThat(runUntil(v, 40, () -> arrivedAt(v, "B"))).isTrue();
            assertThat(v.faults()).isEmpty();
        }
    }
}
