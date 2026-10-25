package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.Element;
import io.github.ksshen0000.vda5050.conformance.rules.StateView.Progress;

/**
 * {@code nodeStates} and {@code edgeStates} list exactly the nodes and edges of the current order still
 * ahead of the vehicle: each traversed node is removed and becomes {@code lastNodeId} /
 * {@code lastNodeSequenceId}, which only move forward. The lists are emptied early only by a cancel.
 */
final class OrderProgressRule extends Rule {

    private final OrderLedger ledger = new OrderLedger();
    private final Map<String, StateView> previous = new HashMap<>();
    /** Vehicles that were sent a cancelOrder since their last order. */
    private final Map<String, Boolean> cancelled = new HashMap<>();

    @Override
    public String id() {
        return "ORDER-PROGRESS";
    }

    @Override
    public String title() {
        return "nodeStates and edgeStates track the order";
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.10.1, 6.10.2 / 3.0: 6.6.1, 6.6.2";
    }

    @Override
    public void onMessage(Message m) {
        String v = m.vehicle();
        ledger.observe(m);
        if (m.topicName().equals("order") && m.hasObjectBody()) {
            cancelled.remove(v);
            return;
        }
        if (m.topicName().equals("instantActions") && m.hasObjectBody()) {
            for (var a : Json.objects(m.body(), "actions")) {
                if ("cancelOrder".equals(Json.text(a, "actionType"))) {
                    cancelled.put(v, true);
                }
            }
            return;
        }
        if (!m.topicName().equals("state") || !m.hasObjectBody()) {
            return;
        }
        StateView state = StateView.of(m);
        StateView before = previous.put(v, state);
        List<OrderView> accepted = state.orderId() == null ? List.of() : ledger.accepted(v, state.orderId());
        if (accepted.isEmpty()) {
            return; // the order was sent before the trace started
        }
        TreeMap<Long, Element> order = route(accepted);
        checked();
        boolean sameOrder = before != null && Objects.equals(before.orderId(), state.orderId());
        Long last = state.lastNodeSequenceId();
        if (sameOrder && last != null && before.lastNodeSequenceId() != null && last < before.lastNodeSequenceId()) {
            error(v, "backwards", "lastNodeSequenceId went back from %d to %d within order %s"
                    .formatted(before.lastNodeSequenceId(), last, state.orderId()), before.message(), m);
        }
        boolean advanced = sameOrder && last != null && !last.equals(before.lastNodeSequenceId());
        if (advanced) {
            Element node = order.get(last);
            if (node == null || !node.node()) {
                error(v, "last node unknown", "lastNodeSequenceId %d is not a node of order %s".formatted(last, state.orderId()), m);
            } else if (!Objects.equals(node.id(), state.lastNodeId())) {
                error(v, "last node id", "lastNodeId is %s but node %d of order %s is %s"
                        .formatted(state.lastNodeId(), last, state.orderId(), node.id()), m);
            }
        }
        checkRemaining(v, m, state, order, state.nodeStates(), "node");
        checkRemaining(v, m, state, order, state.edgeStates(), "edge");
        if (state.nodeStates().isEmpty() && state.edgeStates().isEmpty() && last != null && !cancelled.containsKey(v)) {
            Long lastBase = order.descendingMap().values().stream()
                    .filter(e -> e.node() && Boolean.TRUE.equals(e.released()))
                    .map(Element::sequenceId).findFirst().orElse(null);
            if (lastBase != null && last < lastBase) {
                error(v, "emptied early", "nodeStates is empty but the vehicle only reached sequenceId %d of %d in order %s, without a cancelOrder"
                        .formatted(last, lastBase, state.orderId()), m);
            }
        }
    }

    /**
     * Nodes and edges of the order as the vehicle holds it, by sequenceId: each update replaces the
     * horizon; the base stays as first sent.
     */
    static TreeMap<Long, Element> route(List<OrderView> messages) {
        TreeMap<Long, Element> elements = new TreeMap<>();
        for (OrderView order : messages) {
            Long from = order.nodes().isEmpty() ? null : order.nodes().getFirst().sequenceId();
            if (from != null) {
                elements.tailMap(from, true).values().removeIf(e -> !Boolean.TRUE.equals(e.released()));
            }
            for (Element e : order.nodes()) {
                if (e.sequenceId() != null) {
                    elements.putIfAbsent(e.sequenceId(), e);
                }
            }
            for (Element e : order.edges()) {
                if (e.sequenceId() != null) {
                    elements.putIfAbsent(e.sequenceId(), e);
                }
            }
        }
        return elements;
    }

    private void checkRemaining(String v, Message m, StateView state, TreeMap<Long, Element> order,
            List<Progress> remaining, String kind) {
        Long last = state.lastNodeSequenceId();
        List<Long> listed = new ArrayList<>();
        for (Progress p : remaining) {
            if (p.sequenceId() == null) {
                continue;
            }
            listed.add(p.sequenceId());
            Element element = order.get(p.sequenceId());
            if (element == null || !Objects.equals(element.id(), p.id())) {
                error(v, kind + " unknown", "%sStates lists %s (sequenceId %d), which is not in order %s"
                        .formatted(kind, p.id(), p.sequenceId(), state.orderId()), m);
                continue;
            }
            if (last != null && p.sequenceId() <= last) {
                error(v, kind + " passed", "%sStates still lists %s (sequenceId %d) although lastNodeSequenceId is %d"
                        .formatted(kind, p.id(), p.sequenceId(), last), m);
            }
            if (p.released() != null && element.released() != null && !p.released().equals(element.released())) {
                warning(v, kind + " released", "%sStates reports %s as released=%s but the order says %s"
                        .formatted(kind, p.id(), p.released(), element.released()), m);
            }
        }
        if (last == null || listed.isEmpty()) {
            return;
        }
        long highest = listed.stream().mapToLong(Long::longValue).max().orElseThrow();
        for (Element e : order.subMap(last, false, highest, true).values()) {
            if (e.node() == kind.equals("node") && !listed.contains(e.sequenceId())) {
                error(v, kind + " missing", "%sStates skips %s (sequenceId %d) of order %s, which is still ahead"
                        .formatted(kind, e.id(), e.sequenceId(), state.orderId()), m);
            }
        }
    }
}
