package io.github.ksshen0000.vda5050.sim;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.ActionStatus;
import io.github.ksshen0000.vda5050.v2.ActionTypes;
import io.github.ksshen0000.vda5050.v2.AgvError;
import io.github.ksshen0000.vda5050.v2.AgvPosition;
import io.github.ksshen0000.vda5050.v2.BatteryState;
import io.github.ksshen0000.vda5050.v2.BlockingType;
import io.github.ksshen0000.vda5050.v2.ControlPoint;
import io.github.ksshen0000.vda5050.v2.Edge;
import io.github.ksshen0000.vda5050.v2.EdgeState;
import io.github.ksshen0000.vda5050.v2.ErrorLevel;
import io.github.ksshen0000.vda5050.v2.ErrorReference;
import io.github.ksshen0000.vda5050.v2.ErrorTypes;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.Load;
import io.github.ksshen0000.vda5050.v2.MapStatus;
import io.github.ksshen0000.vda5050.v2.Node;
import io.github.ksshen0000.vda5050.v2.NodeState;
import io.github.ksshen0000.vda5050.v2.OperatingMode;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.OrderRules;
import io.github.ksshen0000.vda5050.v2.SafetyState;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.StateMap;
import io.github.ksshen0000.vda5050.v2.Trajectory;
import io.github.ksshen0000.vda5050.v2.Velocity;
import io.github.ksshen0000.vda5050.v2.Visualization;

/**
 * One simulated line-guided vehicle, following VDA 5050 2.1 semantics. Not thread-safe: all calls
 * must come from the simulation thread. Time advances only through {@link #step(double)}, so tests
 * are fully deterministic.
 *
 * <p>Route bookkeeping: {@code nodes} are the order's nodes not yet traversed and {@code edges} the
 * edges not yet left; {@code edges.get(i)} leads to {@code nodes.get(i)}.
 */
public final class VirtualVehicle {

    /** Heading changes above this make the vehicle stop at the node and rotate on the spot. */
    static final double HEADING_TOLERANCE = Math.toRadians(5);
    /** Lower speed bound while approaching a stop, so the vehicle actually arrives. */
    static final double MIN_CREEP_SPEED = 0.02;

    static final String NO_ORDER_TO_CANCEL = "noOrderToCancel";
    static final String BATTERY_EMPTY = "batteryEmpty";

    private static final Set<String> STANDARD_ORDER_ACTIONS = Set.of(ActionTypes.PICK, ActionTypes.DROP,
            ActionTypes.START_CHARGING, ActionTypes.STOP_CHARGING, ActionTypes.FINE_POSITIONING,
            ActionTypes.DETECT_OBJECT);

    private final String serialNumber;
    private final VehicleProfile profile;
    private SiteMap map;

    private double x;
    private double y;
    private double theta;
    private String mapId;
    private boolean positionInitialized;
    private double velocity;
    private double omega;
    private double distanceSinceLastNode;
    private Double rotationTarget;

    private String orderId = "";
    private long orderUpdateId;
    private String lastNodeId = "";
    private long lastNodeSequenceId;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<Long, double[]> nodePositions = new HashMap<>();
    private final Map<Long, EdgeShape> edgeShapes = new HashMap<>();

    /** Non-null while the vehicle is on an edge (also while braking after a cancel). */
    private EdgeShape currentShape;
    private double s;

    private final Map<String, ActionRuntime> actions = new LinkedHashMap<>();
    private final Deque<ActionRuntime> triggered = new ArrayDeque<>();
    private ActionRuntime cancelling;
    private boolean paused;

    private double battery = 100;
    private boolean charging;
    private final List<Load> loads = new ArrayList<>();
    /** Order rejections, reported until the next order is accepted; one entry per error type. */
    private final Map<String, AgvError> orderErrors = new LinkedHashMap<>();
    private final Map<String, AgvError> vehicleErrors = new LinkedHashMap<>();

    /** Faults injected for testing (SIM-09), by error type. */
    private final Map<String, Fault> faults = new LinkedHashMap<>();

    private boolean stateEvent = true;
    private boolean factsheetRequested;

    /**
     * @param remaining seconds until the fault goes away by itself; infinite if it stays until cleared
     * @param stops     whether the vehicle cannot drive while the fault lasts
     */
    private record Fault(AgvError error, double remaining, boolean stops) {
    }

    public static final String OBSTACLE = "obstacleDetected";

    /**
     * @param initialPose     where the vehicle stands, or {@code null} if its position is unknown until an
     *                        {@code initPosition} instant action
     * @param initialNodeId   node the vehicle stands on, reported as {@code lastNodeId}; may be empty
     */
    public VirtualVehicle(String serialNumber, VehicleProfile profile, SiteMap map, Pose initialPose,
            String initialNodeId) {
        this.serialNumber = serialNumber;
        this.profile = profile;
        this.map = map;
        this.mapId = map == null ? null : map.mapId();
        if (initialPose != null) {
            x = initialPose.x();
            y = initialPose.y();
            theta = Pose.normalize(initialPose.theta());
            positionInitialized = true;
        }
        lastNodeId = initialNodeId == null ? "" : initialNodeId;
    }

    public void setMap(SiteMap map) {
        this.map = map;
        this.mapId = map.mapId();
        markEvent();
    }

    // ------------------------------------------------------------------ orders

    /** Order acceptance as in VDA 5050 2.1 figure 8. */
    public void receiveOrder(Order order) {
        markEvent();
        Map<Long, double[]> positions = new HashMap<>();
        List<String> problems = new ArrayList<>(OrderRules.validate(order));
        for (Node node : order.nodes()) {
            double[] p = resolvePosition(node);
            if (p == null) {
                problems.add("node %s has no nodePosition and is not on the vehicle's map".formatted(node.nodeId()));
            } else {
                positions.put(node.sequenceId(), p);
            }
            if (node.nodePosition() != null && mapId != null && !mapId.equals(node.nodePosition().mapId())) {
                problems.add("node %s is on map %s but the vehicle is on map %s"
                        .formatted(node.nodeId(), node.nodePosition().mapId(), mapId));
            }
        }
        if (!problems.isEmpty()) {
            reject(ErrorTypes.VALIDATION_ERROR, order, String.join("; ", problems));
            return;
        }
        List<String> unsupported = unsupportedActionTypes(order);
        if (!unsupported.isEmpty()) {
            reject(ErrorTypes.ORDER_ERROR, order, "unsupported action types: " + String.join(", ", unsupported));
            return;
        }
        Node first = order.nodes().getFirst();

        if (!order.orderId().equals(orderId)) {
            if (isExecuting()) {
                reject(ErrorTypes.ORDER_UPDATE_ERROR, order, "vehicle is still executing order " + orderId);
            } else if (!isAt(first, positions.get(first.sequenceId()))) {
                reject(ErrorTypes.NO_ROUTE_ERROR, order, "vehicle is not at the first node " + first.nodeId());
            } else {
                acceptNewOrder(order, positions);
            }
            return;
        }
        if (order.orderUpdateId() < orderUpdateId) {
            reject(ErrorTypes.ORDER_UPDATE_ERROR, order,
                    "orderUpdateId %d is older than %d".formatted(order.orderUpdateId(), orderUpdateId));
            return;
        }
        if (order.orderUpdateId() == orderUpdateId) {
            return; // resent duplicate, ignored by the spec
        }
        Node decisionPoint = decisionPoint();
        String expectedId = decisionPoint == null ? lastNodeId : decisionPoint.nodeId();
        long expectedSeq = decisionPoint == null ? lastNodeSequenceId : decisionPoint.sequenceId();
        if (!first.nodeId().equals(expectedId) || first.sequenceId() != expectedSeq) {
            reject(ErrorTypes.ORDER_UPDATE_ERROR, order, "update must start at the decision point %s (sequenceId %d)"
                    .formatted(expectedId, expectedSeq));
            return;
        }
        stitch(order, positions);
    }

    private void acceptNewOrder(Order order, Map<Long, double[]> positions) {
        orderErrors.clear();
        actions.clear();
        triggered.clear();
        nodes.clear();
        edges.clear();
        nodePositions.clear();
        edgeShapes.clear();
        orderId = order.orderId();
        orderUpdateId = order.orderUpdateId();
        nodePositions.putAll(positions);

        Node first = order.nodes().getFirst();
        double[] p = positions.get(first.sequenceId());
        x = p[0];
        y = p[1];
        registerActions(order.nodes(), order.edges());
        nodes.addAll(order.nodes().subList(1, order.nodes().size()));
        edges.addAll(order.edges());
        traverse(first);
    }

    /** Appends an order update at the decision point, replacing the current horizon. */
    private void stitch(Order order, Map<Long, double[]> positions) {
        orderErrors.clear();
        orderUpdateId = order.orderUpdateId();
        while (!nodes.isEmpty() && !nodes.getLast().released()) {
            dropActionsOf(nodes.removeLast().sequenceId());
        }
        while (!edges.isEmpty() && !edges.getLast().released()) {
            Edge removed = edges.removeLast();
            dropActionsOf(removed.sequenceId());
            edgeShapes.remove(removed.sequenceId());
        }
        nodePositions.putAll(positions);
        List<Node> newNodes = order.nodes().subList(1, order.nodes().size());
        registerActions(newNodes, order.edges());
        nodes.addAll(newNodes);
        edges.addAll(order.edges());
    }

    private void reject(String errorType, Order order, String description) {
        orderErrors.put(errorType, new AgvError(errorType, List.of(
                new ErrorReference("headerId", Long.toString(order.headerId())),
                new ErrorReference("orderId", order.orderId()),
                new ErrorReference("orderUpdateId", Long.toString(order.orderUpdateId()))),
                description, null, ErrorLevel.WARNING));
    }

    private boolean isExecuting() {
        return !nodes.isEmpty() || actions.values().stream().anyMatch(a -> !a.status.isTerminal());
    }

    /** Last released node still ahead, or {@code null} if the vehicle already stands on it. */
    private Node decisionPoint() {
        Node result = null;
        for (Node node : nodes) {
            if (!node.released()) {
                break;
            }
            result = node;
        }
        return result;
    }

    private boolean isAt(Node node, double[] position) {
        if (!positionInitialized) {
            return false;
        }
        double tolerance = node.nodePosition() != null && node.nodePosition().allowedDeviationXY() != null
                ? node.nodePosition().allowedDeviationXY()
                : profile.nodeTolerance();
        return Math.hypot(position[0] - x, position[1] - y) <= tolerance + 1e-9;
    }

    private double[] resolvePosition(Node node) {
        if (node.nodePosition() != null) {
            return new double[] {node.nodePosition().x(), node.nodePosition().y()};
        }
        if (map != null) {
            return map.point(node.nodeId()).map(p -> new double[] {p.x(), p.y()}).orElse(null);
        }
        return null;
    }

    private List<String> unsupportedActionTypes(Order order) {
        List<Action> all = new ArrayList<>();
        order.nodes().forEach(n -> all.addAll(n.actions()));
        order.edges().forEach(e -> all.addAll(e.actions()));
        return all.stream()
                .map(Action::actionType)
                .filter(t -> !STANDARD_ORDER_ACTIONS.contains(t) && !profile.customActionTypes().contains(t))
                .distinct()
                .toList();
    }

    private void registerActions(List<Node> newNodes, List<Edge> newEdges) {
        for (Node node : newNodes) {
            node.actions().forEach(a -> actions.put(a.actionId(),
                    new ActionRuntime(a, ActionRuntime.Scope.NODE, node.sequenceId())));
        }
        for (Edge edge : newEdges) {
            edge.actions().forEach(a -> actions.put(a.actionId(),
                    new ActionRuntime(a, ActionRuntime.Scope.EDGE, edge.sequenceId())));
        }
    }

    private void dropActionsOf(long sequenceId) {
        actions.values().removeIf(a -> a.scope != ActionRuntime.Scope.INSTANT && a.ownerSequenceId == sequenceId
                && a.status == ActionStatus.WAITING);
    }

    // ------------------------------------------------------------------ instant actions

    public void receiveInstantActions(InstantActions instantActions) {
        markEvent();
        for (Action action : instantActions.actions()) {
            ActionRuntime runtime = new ActionRuntime(action, ActionRuntime.Scope.INSTANT, -1);
            actions.put(action.actionId(), runtime);
            switch (action.actionType()) {
                case ActionTypes.START_PAUSE -> {
                    paused = true;
                    runtime.finish(null);
                }
                case ActionTypes.STOP_PAUSE -> {
                    paused = false;
                    runtime.finish(null);
                }
                case ActionTypes.CANCEL_ORDER -> cancelOrder(runtime);
                case ActionTypes.INIT_POSITION -> initPosition(runtime);
                case ActionTypes.STATE_REQUEST -> runtime.finish(null);
                case ActionTypes.FACTSHEET_REQUEST -> {
                    factsheetRequested = true;
                    runtime.finish(null);
                }
                case ActionTypes.START_CHARGING, ActionTypes.STOP_CHARGING -> triggered.add(runtime);
                default -> runtime.fail("unsupported instant action " + action.actionType());
            }
        }
    }

    private void cancelOrder(ActionRuntime runtime) {
        boolean hasOrder = !nodes.isEmpty() || actions.values().stream()
                .anyMatch(a -> a.scope != ActionRuntime.Scope.INSTANT && !a.status.isTerminal());
        if (!hasOrder) {
            runtime.fail("no order to cancel");
            vehicleErrors.put(NO_ORDER_TO_CANCEL, new AgvError(NO_ORDER_TO_CANCEL,
                    List.of(new ErrorReference("actionId", runtime.action.actionId())),
                    "received cancelOrder without an active order", null, ErrorLevel.WARNING));
            return;
        }
        for (ActionRuntime a : actions.values()) {
            if (a.scope != ActionRuntime.Scope.INSTANT && !a.status.isTerminal()) {
                a.fail("order cancelled");
            }
        }
        triggered.removeIf(a -> a.scope != ActionRuntime.Scope.INSTANT);
        // On an edge, drive on to its end node and stop there (VDA 5050 leaves the choice to the
        // vehicle): standing on a node, it can be given a new route right away. Its actions are failed.
        Edge current = currentShape == null ? null : edges.getFirst();
        Node next = currentShape == null ? null : nodes.getFirst();
        nodes.clear();
        edges.clear();
        if (current != null) {
            edges.add(current);
            nodes.add(next);
        }
        rotationTarget = null;
        runtime.status = ActionStatus.RUNNING;
        cancelling = runtime;
    }

    private void initPosition(ActionRuntime runtime) {
        if (!nodes.isEmpty()) {
            runtime.fail("vehicle has an active order");
            return;
        }
        Action a = runtime.action;
        Object px = a.parameter("x").orElse(null);
        Object py = a.parameter("y").orElse(null);
        if (!(px instanceof Number nx) || !(py instanceof Number ny)) {
            runtime.fail("initPosition needs numeric x and y");
            return;
        }
        x = nx.doubleValue();
        y = ny.doubleValue();
        theta = a.parameter("theta").filter(Number.class::isInstance).map(t -> Pose.normalize(((Number) t).doubleValue()))
                .orElse(theta);
        a.parameter("mapId").map(Object::toString).ifPresent(m -> mapId = m);
        lastNodeId = a.parameter("lastNodeId").map(Object::toString).orElse("");
        lastNodeSequenceId = 0;
        positionInitialized = true;
        velocity = 0;
        currentShape = null;
        runtime.finish(null);
    }

    // ------------------------------------------------------------------ simulation

    public void step(double dt) {
        updateBattery(dt);
        expireFaults(dt);
        if (paused || battery <= 0 || stoppedByFault()) {
            brake(dt);
            return;
        }
        progressActions(dt);
        startTriggeredActions();
        if (cancelling != null) {
            if (currentShape != null) {
                drive(dt);
                return;
            }
            velocity = 0;
            cancelling.finish(null);
            cancelling = null;
            markEvent();
            return;
        }
        move(dt);
    }

    private void move(double dt) {
        if (currentShape != null) {
            drive(dt);
            return;
        }
        if (rotationTarget != null) {
            rotate(dt);
            return;
        }
        omega = 0;
        if (drivingBlocked() || edges.isEmpty() || !edges.getFirst().released()) {
            return;
        }
        EdgeShape shape = shapeOf(edges.getFirst());
        if (Math.abs(Pose.angleDiff(theta, shape.startHeading())) > HEADING_TOLERANCE) {
            rotationTarget = shape.startHeading();
            rotate(dt);
            return;
        }
        theta = shape.startHeading();
        enterEdge();
        if (!drivingBlocked()) {
            drive(dt);
        }
    }

    private void drive(double dt) {
        Edge edge = edges.getFirst();
        double limit = edge.maxSpeed() == null ? profile.maxSpeed() : Math.min(profile.maxSpeed(), edge.maxSpeed());
        double toStop = distanceToStop();
        double target = Math.min(limit, Math.sqrt(2 * profile.deceleration() * toStop));
        if (toStop > 1e-9) {
            target = Math.max(target, Math.min(MIN_CREEP_SPEED, limit));
        }
        velocity = velocity < target
                ? Math.min(target, velocity + profile.acceleration() * dt)
                : Math.max(target, velocity - profile.deceleration() * dt);
        double ds = velocity * dt;
        if (ds >= toStop) {
            ds = toStop;
        }
        advance(ds);
    }

    /** Moves {@code ds} meters along the route, passing through nodes where no stop is required. */
    private void advance(double ds) {
        while (true) {
            double remaining = currentShape.length() - s;
            if (ds < remaining) {
                s += ds;
                travelled(ds);
                applyPose(currentShape.at(s));
                return;
            }
            ds -= remaining;
            travelled(remaining);
            boolean passThrough = !mustStopAt(0);
            arriveAtNextNode();
            if (!passThrough) {
                velocity = 0;
                return;
            }
            enterEdge();
            if (ds <= 0) {
                return;
            }
        }
    }

    private void brake(double dt) {
        omega = 0;
        velocity = Math.max(0, velocity - profile.deceleration() * dt);
        if (currentShape != null && velocity > 0) {
            double ds = Math.min(velocity * dt, currentShape.length() - s);
            s += ds;
            travelled(ds);
            applyPose(currentShape.at(s));
        }
    }

    private void rotate(double dt) {
        double diff = Pose.angleDiff(theta, rotationTarget);
        double stepAngle = profile.rotationSpeed() * dt;
        if (Math.abs(diff) <= stepAngle) {
            theta = Pose.normalize(rotationTarget);
            rotationTarget = null;
            omega = 0;
            markEvent();
        } else {
            omega = Math.signum(diff) * profile.rotationSpeed();
            theta = Pose.normalize(theta + Math.signum(diff) * stepAngle);
        }
    }

    private void enterEdge() {
        Edge edge = edges.getFirst();
        currentShape = shapeOf(edge);
        s = 0;
        trigger(edge.sequenceId(), ActionRuntime.Scope.EDGE);
        markEvent();
    }

    private void arriveAtNextNode() {
        Edge edge = edges.removeFirst();
        Node node = nodes.removeFirst();
        theta = currentShape.endHeading();
        currentShape = null;
        s = 0;
        for (ActionRuntime a : actions.values()) {
            if (a.scope == ActionRuntime.Scope.EDGE && a.ownerSequenceId == edge.sequenceId() && !a.status.isTerminal()) {
                a.finish("edge left");
                triggered.remove(a);
            }
        }
        double[] p = nodePositions.get(node.sequenceId());
        x = p[0];
        y = p[1];
        traverse(node);
    }

    /** Reports the node as traversed and triggers its actions. */
    private void traverse(Node node) {
        lastNodeId = node.nodeId();
        lastNodeSequenceId = node.sequenceId();
        distanceSinceLastNode = 0;
        trigger(node.sequenceId(), ActionRuntime.Scope.NODE);
        boolean routeEndsHere = edges.isEmpty() || !edges.getFirst().released();
        if (routeEndsHere && node.nodePosition() != null && node.nodePosition().theta() != null
                && Math.abs(Pose.angleDiff(theta, node.nodePosition().theta())) > 1e-3) {
            rotationTarget = node.nodePosition().theta();
        }
        markEvent();
    }

    /** Whether the vehicle must come to a standstill at {@code nodes.get(i)}. */
    private boolean mustStopAt(int i) {
        if (i + 1 >= edges.size()) {
            return true;
        }
        Node node = nodes.get(i);
        Edge next = edges.get(i + 1);
        if (!next.released()) {
            return true;
        }
        if (node.actions().stream().anyMatch(this::blocksDriving) || next.actions().stream().anyMatch(this::blocksDriving)) {
            return true;
        }
        double turn = Pose.angleDiff(shapeOf(edges.get(i)).endHeading(), shapeOf(next).startHeading());
        return Math.abs(turn) > HEADING_TOLERANCE;
    }

    private double distanceToStop() {
        double distance = currentShape.length() - s;
        int i = 0;
        while (!mustStopAt(i)) {
            i++;
            distance += shapeOf(edges.get(i)).length();
        }
        return distance;
    }

    private boolean blocksDriving(Action action) {
        return action.blockingType() == null || action.blockingType() != BlockingType.NONE;
    }

    private void travelled(double ds) {
        distanceSinceLastNode += ds;
        battery = Math.max(0, battery - ds * profile.drainPerMeter());
    }

    private void applyPose(Pose pose) {
        x = pose.x();
        y = pose.y();
        theta = pose.theta();
    }

    private EdgeShape shapeOf(Edge edge) {
        return edgeShapes.computeIfAbsent(edge.sequenceId(), seq -> {
            double[] start = nodePositions.get(edge.sequenceId() - 1);
            double[] end = nodePositions.get(edge.sequenceId() + 1);
            Trajectory t = edge.trajectory();
            if (t != null && t.degree() == 3 && t.controlPoints().size() == 4) {
                List<ControlPoint> c = t.controlPoints();
                return EdgeShape.cubicBezier(xy(c.get(0)), xy(c.get(1)), xy(c.get(2)), xy(c.get(3)));
            }
            if (t != null && t.degree() == 1 && t.controlPoints().size() >= 2) {
                return EdgeShape.polyline(t.controlPoints().stream().map(VirtualVehicle::xy).toList());
            }
            if (map != null) {
                var fromMap = map.pathShape(edge.edgeId(), edge.startNodeId());
                if (fromMap.isPresent()) {
                    return fromMap.get();
                }
            }
            return EdgeShape.line(start[0], start[1], end[0], end[1]);
        });
    }

    private static double[] xy(ControlPoint c) {
        return new double[] {c.x(), c.y()};
    }

    // ------------------------------------------------------------------ actions

    private void trigger(long sequenceId, ActionRuntime.Scope scope) {
        for (ActionRuntime a : actions.values()) {
            if (a.scope == scope && a.ownerSequenceId == sequenceId && a.status == ActionStatus.WAITING) {
                triggered.add(a);
            }
        }
    }

    private boolean drivingBlocked() {
        return triggered.stream().anyMatch(ActionRuntime::blocksDriving)
                || actions.values().stream().anyMatch(a -> a.isActive() && a.blocksDriving());
    }

    /** Starts triggered actions in order, honouring HARD (exclusive) and SOFT/NONE (parallel) blocking. */
    private void startTriggeredActions() {
        while (!triggered.isEmpty()) {
            ActionRuntime head = triggered.peek();
            boolean anyRunning = actions.values().stream().anyMatch(ActionRuntime::isActive);
            boolean hardRunning = actions.values().stream()
                    .anyMatch(a -> a.isActive() && a.blocking() == BlockingType.HARD);
            if (hardRunning || (head.blocking() == BlockingType.HARD && anyRunning)) {
                return;
            }
            triggered.poll();
            start(head);
            if (head.blocking() == BlockingType.HARD) {
                return;
            }
        }
    }

    private void start(ActionRuntime a) {
        markEvent();
        if (a.type().equals(ActionTypes.START_CHARGING) && map != null && !map.points().isEmpty()
                && !map.isChargingPoint(lastNodeId)) {
            a.fail("not on a charging point");
            return;
        }
        a.status = ActionStatus.RUNNING;
        a.remainingSeconds = profile.durationOf(a.type());
    }

    private void progressActions(double dt) {
        for (ActionRuntime a : actions.values()) {
            if (a.status == ActionStatus.RUNNING && a != cancelling) {
                a.remainingSeconds -= dt;
                if (a.remainingSeconds <= 1e-9) {
                    complete(a);
                }
            }
        }
    }

    private void complete(ActionRuntime a) {
        switch (a.type()) {
            case ActionTypes.PICK -> {
                loads.clear();
                loads.add(new Load(a.action.parameter("loadId").map(Object::toString).orElse(null),
                        a.action.parameter("loadType").map(Object::toString).orElse(null), null, null, null, null));
            }
            case ActionTypes.DROP -> loads.clear();
            case ActionTypes.START_CHARGING -> charging = true;
            case ActionTypes.STOP_CHARGING -> charging = false;
            default -> {
                // finePositioning, detectObject and custom actions only take time
            }
        }
        a.finish(null);
        markEvent();
    }

    private void updateBattery(double dt) {
        if (charging) {
            battery = Math.min(100, battery + profile.chargePerSecond() * dt);
        } else {
            battery = Math.max(0, battery - profile.drainPerSecond() * dt);
        }
        if (battery <= 0 && !vehicleErrors.containsKey(BATTERY_EMPTY)) {
            vehicleErrors.put(BATTERY_EMPTY, new AgvError(BATTERY_EMPTY, null, "battery is empty", null, ErrorLevel.FATAL));
            markEvent();
        }
    }

    // ------------------------------------------------------------------ fault injection (SIM-09)

    /**
     * Reports an error until it is cleared or {@code seconds} have passed ({@code <= 0}: until cleared).
     * A FATAL error also stops the vehicle, as real vehicles do.
     */
    public void injectError(String errorType, ErrorLevel level, String description, double seconds) {
        faults.put(errorType, new Fault(new AgvError(errorType, null, description, null, level),
                seconds > 0 ? seconds : Double.POSITIVE_INFINITY, level == ErrorLevel.FATAL));
        markEvent();
    }

    /** Something stands in the way: the vehicle stops for {@code seconds} and reports a warning meanwhile. */
    public void blockByObstacle(double seconds) {
        faults.put(OBSTACLE, new Fault(new AgvError(OBSTACLE, null, "obstacle in the driving path", null, ErrorLevel.WARNING),
                seconds > 0 ? seconds : Double.POSITIVE_INFINITY, true));
        markEvent();
    }

    public void clearFaults() {
        if (!faults.isEmpty()) {
            faults.clear();
            markEvent();
        }
    }

    public List<String> faults() {
        return List.copyOf(faults.keySet());
    }

    private void expireFaults(double dt) {
        if (faults.isEmpty()) {
            return;
        }
        faults.replaceAll((type, f) -> new Fault(f.error(), f.remaining() - dt, f.stops()));
        if (faults.values().removeIf(f -> f.remaining() <= 0)) {
            markEvent();
        }
    }

    private boolean stoppedByFault() {
        return faults.values().stream().anyMatch(Fault::stops);
    }

    // ------------------------------------------------------------------ reporting

    /** Current state without header. */
    public State state() {
        List<NodeState> nodeStates = nodes.stream()
                .map(n -> new NodeState(n.nodeId(), n.sequenceId(), n.nodeDescription(), n.released(), n.nodePosition()))
                .toList();
        List<EdgeState> edgeStates = edges.stream()
                .map(e -> new EdgeState(e.edgeId(), e.sequenceId(), e.edgeDescription(), e.released(), e.trajectory()))
                .toList();
        List<StateMap> maps = map == null ? null : List.of(new StateMap(map.mapId(), map.version(), null, MapStatus.ENABLED));
        List<AgvError> errors = new ArrayList<>(orderErrors.values());
        errors.addAll(vehicleErrors.values());
        faults.values().forEach(f -> errors.add(f.error()));
        double reach = profile.drainPerMeter() > 0 ? battery / profile.drainPerMeter() : 0;
        return State.of(maps, orderId, orderUpdateId, lastNodeId, lastNodeSequenceId, isDriving(), paused, false,
                distanceSinceLastNode, OperatingMode.AUTOMATIC, nodeStates, edgeStates, agvPosition(),
                new Velocity(velocity, 0.0, omega), List.copyOf(loads),
                actions.values().stream().map(ActionRuntime::toState).toList(),
                new BatteryState(Math.round(battery * 10) / 10.0, null, null, charging, Math.floor(reach)),
                errors, SafetyState.CLEAR);
    }

    public Visualization visualization() {
        return new Visualization(Header.UNSTAMPED, agvPosition(), new Velocity(velocity, 0.0, omega));
    }

    private AgvPosition agvPosition() {
        return new AgvPosition(x, y, theta, mapId == null ? "" : mapId, null, positionInitialized, null, null);
    }

    /** True once after anything changed that the spec wants reported immediately. */
    public boolean takeStateEvent() {
        boolean event = stateEvent;
        stateEvent = false;
        return event;
    }

    public boolean takeFactsheetRequest() {
        boolean requested = factsheetRequested;
        factsheetRequested = false;
        return requested;
    }

    private void markEvent() {
        stateEvent = true;
    }

    public boolean isDriving() {
        return velocity > 0 || omega != 0;
    }

    public Pose pose() {
        return new Pose(x, y, theta);
    }

    public double velocity() {
        return velocity;
    }

    public String serialNumber() {
        return serialNumber;
    }

    public VehicleProfile profile() {
        return profile;
    }

    public String orderId() {
        return orderId;
    }

    public String lastNodeId() {
        return lastNodeId;
    }

    public double battery() {
        return battery;
    }

    /** For tests and fault injection. */
    public void setBattery(double percent) {
        battery = Math.max(0, Math.min(100, percent));
        markEvent();
    }

    public boolean isCharging() {
        return charging;
    }

    public List<Load> loads() {
        return List.copyOf(loads);
    }
}
