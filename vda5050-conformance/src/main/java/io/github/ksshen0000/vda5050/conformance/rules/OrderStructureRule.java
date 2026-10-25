package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.Dialect;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.ActionView;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.Element;

/**
 * An order alternates nodes and edges with sequence ids 0, 1, 2, ... (nodes even, edges odd), each edge
 * connects its neighbouring nodes, the base comes before the horizon and ends on a node, and action ids
 * are unique within the order.
 */
final class OrderStructureRule extends Rule {

    @Override
    public String id() {
        return "ORDER-STRUCTURE";
    }

    @Override
    public String title() {
        return "Order is well-formed";
    }

    @Override
    public Party party() {
        return Party.FLEET_CONTROL;
    }

    @Override
    public String section() {
        return "2.x: 6.6.1, 6.6.2 / 3.0: 6.1.1, 6.1.2";
    }

    @Override
    public void onMessage(Message m) {
        if (!m.topicName().equals("order") || !m.hasObjectBody()) {
            return;
        }
        checked();
        OrderView order = OrderView.of(m);
        String v = m.vehicle();
        List<Element> nodes = order.nodes();
        List<Element> edges = order.edges();
        if (order.orderId() == null || order.orderId().isEmpty()) {
            error(v, "orderId", "orderId is missing or empty", m);
        }
        if (nodes.isEmpty()) {
            error(v, "no nodes", order.label() + " has no nodes", m);
            return;
        }
        if (edges.size() != nodes.size() - 1) {
            error(v, "counts", "%s has %d nodes and %d edges; there shall be one edge less than nodes"
                    .formatted(order.label(), nodes.size(), edges.size()), m);
            return;
        }
        Long first = nodes.getFirst().sequenceId();
        if (first != null && first % 2 != 0) {
            error(v, "parity", "%s starts with node sequenceId %d; node sequenceIds are even".formatted(order.label(), first), m);
        }
        if (!Boolean.TRUE.equals(nodes.getFirst().released())) {
            error(v, "first released", "%s: the first node shall be released".formatted(order.label()), m);
        }
        boolean horizon = false;
        for (int i = 0; i < edges.size(); i++) {
            Element start = nodes.get(i);
            Element edge = edges.get(i);
            Element end = nodes.get(i + 1);
            if (start.sequenceId() != null && edge.sequenceId() != null && end.sequenceId() != null
                    && (edge.sequenceId() != start.sequenceId() + 1 || end.sequenceId() != edge.sequenceId() + 1)) {
                error(v, "sequence", "%s: sequenceIds %d, %d, %d of node %s, edge %s, node %s do not increase by one"
                        .formatted(order.label(), start.sequenceId(), edge.sequenceId(), end.sequenceId(), start.id(),
                                edge.id(), end.id()), m);
            }
            // startNodeId/endNodeId are mandatory in 2.x and optional in 3.0; check them when given
            boolean v2 = m.dialect() == Dialect.V2;
            if ((v2 || edge.startNodeId() != null) && !Objects.equals(edge.startNodeId(), start.id())
                    || (v2 || edge.endNodeId() != null) && !Objects.equals(edge.endNodeId(), end.id())) {
                error(v, "edge ends", "%s: edge %s goes from %s to %s but sits between nodes %s and %s"
                        .formatted(order.label(), edge.id(), edge.startNodeId(), edge.endNodeId(), start.id(), end.id()), m);
            }
            horizon |= !Boolean.TRUE.equals(edge.released());
            if (horizon && Boolean.TRUE.equals(edge.released())) {
                error(v, "released after horizon", "%s: edge %s is released after the horizon started".formatted(order.label(), edge.id()), m);
            }
            if (Boolean.TRUE.equals(edge.released()) && !Boolean.TRUE.equals(end.released())) {
                error(v, "base ends on edge", "%s: edge %s is released but its end node %s is not; the base ends on a node"
                        .formatted(order.label(), edge.id(), end.id()), m);
            }
            horizon |= !Boolean.TRUE.equals(end.released());
            if (horizon && Boolean.TRUE.equals(end.released())) {
                error(v, "released after horizon", "%s: node %s is released after the horizon started".formatted(order.label(), end.id()), m);
            }
        }
        Set<String> actionIds = new HashSet<>();
        for (ActionView action : order.allActions()) {
            if (action.actionId() != null && !actionIds.add(action.actionId())) {
                error(v, "duplicate action", "%s: actionId %s is used more than once".formatted(order.label(), action.actionId()), m);
            }
        }
    }
}
