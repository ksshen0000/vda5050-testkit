package io.github.ksshen0000.vda5050.sim;

import java.util.ArrayList;
import java.util.List;

import io.github.ksshen0000.vda5050.v2.Action;
import io.github.ksshen0000.vda5050.v2.Edge;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.Node;
import io.github.ksshen0000.vda5050.v2.NodePosition;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.Trajectory;

/**
 * Builds orders for tests: {@code start("o1", 0).node("A", 0, 0).node("B", 10, 0).build()} gives a
 * released A -> B order with sequence ids assigned automatically.
 */
final class OrderBuilder {

    static final String MAP = "TEST";

    private final String orderId;
    private final long updateId;
    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private long nextSequenceId;
    private boolean released = true;
    private Trajectory nextTrajectory;
    private Double nextMaxSpeed;
    private final List<Action> nextEdgeActions = new ArrayList<>();

    private OrderBuilder(String orderId, long updateId, long firstSequenceId) {
        this.orderId = orderId;
        this.updateId = updateId;
        this.nextSequenceId = firstSequenceId;
    }

    static OrderBuilder start(String orderId, long updateId) {
        return new OrderBuilder(orderId, updateId, 0);
    }

    /** An update whose first node carries {@code firstSequenceId} (the decision point). */
    static OrderBuilder update(String orderId, long updateId, long firstSequenceId) {
        return new OrderBuilder(orderId, updateId, firstSequenceId);
    }

    /** Everything added after this call belongs to the horizon. */
    OrderBuilder horizon() {
        released = false;
        return this;
    }

    OrderBuilder trajectory(Trajectory trajectory) {
        nextTrajectory = trajectory;
        return this;
    }

    OrderBuilder maxSpeed(double maxSpeed) {
        nextMaxSpeed = maxSpeed;
        return this;
    }

    OrderBuilder edgeAction(Action action) {
        nextEdgeActions.add(action);
        return this;
    }

    OrderBuilder node(String id, double x, double y, Action... actions) {
        return node(id, NodePosition.of(x, y, null, MAP), actions);
    }

    OrderBuilder nodeWithTheta(String id, double x, double y, double theta) {
        return node(id, NodePosition.of(x, y, theta, MAP));
    }

    /** A node without position, to be resolved from the vehicle's map. */
    OrderBuilder mapNode(String id) {
        return node(id, null);
    }

    OrderBuilder node(String id, NodePosition position, Action... actions) {
        if (!nodes.isEmpty()) {
            Node previous = nodes.getLast();
            edges.add(new Edge(previous.nodeId() + id, nextSequenceId++, null, released, previous.nodeId(), id,
                    nextMaxSpeed, null, null, null, null, null, null, null, null, nextTrajectory, null,
                    List.copyOf(nextEdgeActions)));
            nextTrajectory = null;
            nextMaxSpeed = null;
            nextEdgeActions.clear();
        }
        nodes.add(new Node(id, nextSequenceId++, null, released, position, List.of(actions)));
        return this;
    }

    Order build() {
        return new Order(Header.UNSTAMPED, orderId, updateId, nodes, edges);
    }
}
