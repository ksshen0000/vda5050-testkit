package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.ArrayList;
import java.util.List;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import tools.jackson.databind.JsonNode;

/**
 * The parts of an order message the behaviour checks need. Fields of the wrong type read as
 * {@code null}; the schema check reports those.
 */
record OrderView(Message message, String orderId, Long orderUpdateId, List<Element> nodes, List<Element> edges) {

    /**
     * A node or an edge.
     *
     * @param node        true for a node, false for an edge
     * @param id          {@code nodeId} or {@code edgeId}
     * @param startNodeId edges only, {@code null} for nodes (and for 3.0 edges, where it is optional)
     */
    record Element(boolean node, String id, Long sequenceId, Boolean released, String startNodeId, String endNodeId,
            List<ActionView> actions, JsonNode json) {
    }

    record ActionView(String actionId, String actionType, String blockingType) {
    }

    static OrderView of(Message m) {
        JsonNode body = m.body();
        List<Element> nodes = new ArrayList<>();
        for (JsonNode n : Json.objects(body, "nodes")) {
            nodes.add(new Element(true, Json.text(n, "nodeId"), Json.integer(n, "sequenceId"), Json.bool(n, "released"),
                    null, null, actions(n), n));
        }
        List<Element> edges = new ArrayList<>();
        for (JsonNode e : Json.objects(body, "edges")) {
            edges.add(new Element(false, Json.text(e, "edgeId"), Json.integer(e, "sequenceId"), Json.bool(e, "released"),
                    Json.text(e, "startNodeId"), Json.text(e, "endNodeId"), actions(e), e));
        }
        return new OrderView(m, Json.text(body, "orderId"), Json.integer(body, "orderUpdateId"), nodes, edges);
    }

    static List<ActionView> actions(JsonNode holder) {
        List<ActionView> actions = new ArrayList<>();
        for (JsonNode a : Json.objects(holder, "actions")) {
            actions.add(new ActionView(Json.text(a, "actionId"), Json.text(a, "actionType"), Json.text(a, "blockingType")));
        }
        return actions;
    }

    /** Released nodes in order, i.e. the base. */
    List<Element> baseNodes() {
        return nodes.stream().takeWhile(n -> Boolean.TRUE.equals(n.released())).toList();
    }

    /** Last released node: where the vehicle stops unless the base is extended. */
    Element decisionPoint() {
        List<Element> base = baseNodes();
        return base.isEmpty() ? null : base.getLast();
    }

    List<ActionView> allActions() {
        List<ActionView> all = new ArrayList<>();
        nodes.forEach(n -> all.addAll(n.actions()));
        edges.forEach(e -> all.addAll(e.actions()));
        return all;
    }

    String label() {
        return "order %s/%s".formatted(orderId, orderUpdateId);
    }
}
