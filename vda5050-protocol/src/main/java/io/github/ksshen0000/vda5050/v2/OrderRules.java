package io.github.ksshen0000.vda5050.v2;

import java.util.ArrayList;
import java.util.List;

/**
 * Structural rules of a single order message (VDA 5050 2.1 section 6.6). Rules about how an update
 * continues a previous order depend on the vehicle's state and are checked by the receiver.
 */
public final class OrderRules {

    private OrderRules() {
    }

    /** Returns human-readable violations; an empty list means the order is well-formed. */
    public static List<String> validate(Order order) {
        List<String> problems = new ArrayList<>();
        if (order.orderId() == null || order.orderId().isEmpty()) {
            problems.add("orderId is empty");
        }
        List<Node> nodes = order.nodes();
        List<Edge> edges = order.edges();
        if (nodes.isEmpty()) {
            problems.add("order has no nodes");
            return problems;
        }
        if (edges.size() != nodes.size() - 1) {
            problems.add("order has %d nodes but %d edges, expected %d".formatted(nodes.size(), edges.size(), nodes.size() - 1));
            return problems;
        }
        if (nodes.getFirst().sequenceId() % 2 != 0) {
            problems.add("node sequenceIds must be even, first is %d".formatted(nodes.getFirst().sequenceId()));
        }
        if (!nodes.getFirst().released()) {
            problems.add("first node must be released");
        }
        boolean inHorizon = false;
        for (int i = 0; i < edges.size(); i++) {
            Node start = nodes.get(i);
            Edge edge = edges.get(i);
            Node end = nodes.get(i + 1);
            if (edge.sequenceId() != start.sequenceId() + 1 || end.sequenceId() != edge.sequenceId() + 1) {
                problems.add("sequenceIds must increase by one: node %d, edge %d, node %d"
                        .formatted(start.sequenceId(), edge.sequenceId(), end.sequenceId()));
            }
            if (!start.nodeId().equals(edge.startNodeId()) || !end.nodeId().equals(edge.endNodeId())) {
                problems.add("edge %s must connect %s to %s".formatted(edge.edgeId(), start.nodeId(), end.nodeId()));
            }
            inHorizon |= !edge.released();
            if (inHorizon && edge.released()) {
                problems.add("edge %s is released after the horizon started".formatted(edge.edgeId()));
            }
            if (edge.released() && !end.released()) {
                problems.add("base must end on a node: edge %s is released but node %s is not"
                        .formatted(edge.edgeId(), end.nodeId()));
            }
            inHorizon |= !end.released();
            if (inHorizon && end.released()) {
                problems.add("node %s is released after the horizon started".formatted(end.nodeId()));
            }
        }
        return problems;
    }

    /** The released prefix of the nodes. */
    public static List<Node> baseNodes(Order order) {
        return order.nodes().stream().takeWhile(Node::released).toList();
    }

    /** The released prefix of the edges. */
    public static List<Edge> baseEdges(Order order) {
        return order.edges().stream().takeWhile(Edge::released).toList();
    }
}
