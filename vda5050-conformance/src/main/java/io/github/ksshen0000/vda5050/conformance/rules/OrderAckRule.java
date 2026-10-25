package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/**
 * After an order, the vehicle's next state either shows it ({@code orderId} and {@code orderUpdateId})
 * or reports why it was rejected. An outdated update (lower {@code orderUpdateId} than the vehicle's)
 * must be rejected.
 */
final class OrderAckRule extends Rule {

    /** Error types announcing an order rejection, 2.x and 3.0. */
    static final Set<String> REJECTIONS = Set.of(
            "validationError", "orderError", "orderUpdateError", "noRouteError",
            "VALIDATION_FAILURE", "INVALID_ORDER_ACTION", "OUTDATED_ORDER_UPDATE", "SAME_ORDER_UPDATE_ID",
            "ORDER_UPDATE_FOLLOWING_CANCEL", "NO_ROUTE_TO_TARGET", "OTHER_ORDER_ACTIVE", "START_NODE_OUT_OF_RANGE",
            "MOBILE_ROBOT_NOT_AVAILABLE", "UNKNOWN_MAP_ID", "INSUFFICIENT_MEMORY", "UNSUPPORTED_PARAMETER",
            "NODE_UNREACHABLE");
    static final Set<String> OUTDATED = Set.of("orderUpdateError", "OUTDATED_ORDER_UPDATE");

    private final Duration reactionTime;
    private final Map<String, List<Pending>> pending = new HashMap<>();
    private final Map<String, StateView> lastState = new HashMap<>();

    /** @param outdated the vehicle already had a higher orderUpdateId of the same order */
    private record Pending(OrderView order, Instant deadline, boolean outdated) {
    }

    OrderAckRule(Duration reactionTime) {
        this.reactionTime = reactionTime;
    }

    @Override
    public String id() {
        return "ORDER-ACK";
    }

    @Override
    public String title() {
        return "Vehicle accepts or rejects every order";
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.6.2 (figure 8), 6.6.4, 6.10 / 3.0: 6.1.2, 6.1.4, 6.6";
    }

    @Override
    public void onMessage(Message m) {
        String v = m.vehicle();
        if (m.topicName().equals("order") && m.hasObjectBody()) {
            OrderView order = OrderView.of(m);
            if (order.orderId() == null || order.orderUpdateId() == null) {
                return;
            }
            StateView current = lastState.get(v);
            boolean outdated = current != null && order.orderId().equals(current.orderId())
                    && current.orderUpdateId() != null && order.orderUpdateId() < current.orderUpdateId();
            pending.computeIfAbsent(v, k -> new ArrayList<>())
                    .add(new Pending(order, m.receivedAt().plus(reactionTime), outdated));
            return;
        }
        if (!m.topicName().equals("state") || !m.hasObjectBody()) {
            return;
        }
        StateView state = StateView.of(m);
        lastState.put(v, state);
        List<Pending> open = pending.get(v);
        if (open == null) {
            return;
        }
        for (Iterator<Pending> it = open.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            OrderView order = p.order();
            boolean shows = order.orderId().equals(state.orderId()) && state.orderUpdateId() != null;
            boolean rejected = state.hasError(REJECTIONS) || state.errorReferences("orderId", order.orderId());
            if (p.outdated()) {
                if (shows && state.orderUpdateId().equals(order.orderUpdateId())) {
                    checked();
                    it.remove();
                    error(v, "outdated accepted", "%s is outdated but the vehicle took it over".formatted(order.label()),
                            order.message(), m);
                } else if (state.hasError(OUTDATED) || rejected) {
                    checked();
                    it.remove();
                } else if (m.receivedAt().isAfter(p.deadline())) {
                    checked();
                    it.remove();
                    warning(v, "outdated not reported", "%s is outdated; the vehicle shall report orderUpdateError (3.0: OUTDATED_ORDER_UPDATE)"
                            .formatted(order.label()), order.message(), m);
                }
                continue;
            }
            if (shows && state.orderUpdateId() >= order.orderUpdateId() || rejected) {
                checked();
                it.remove();
            } else if (m.receivedAt().isAfter(p.deadline())) {
                checked();
                it.remove();
                error(v, "not acknowledged", "%s neither shows up in state within %.1f s nor is rejected; state has order %s/%s"
                        .formatted(order.label(), reactionTime.toMillis() / 1000.0, state.orderId(), state.orderUpdateId()),
                        order.message(), m);
            }
        }
    }

    @Override
    public void onEnd(Instant end) {
        pending.forEach((v, open) -> open.forEach(p -> {
            if (end.isAfter(p.deadline())) {
                checked();
                error(v, "no state", "no state after %s although the vehicle shall report receiving an order"
                        .formatted(p.order().label()), p.order().message());
            } else {
                inconclusive("%s: trace ends before the vehicle reacted to %s".formatted(v, p.order().label()));
            }
        }));
    }
}
