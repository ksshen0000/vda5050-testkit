package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Message;

/**
 * Which order messages each vehicle took over. An order is accepted once a state shows its orderId
 * with at least its orderUpdateId, and rejected if a state reports a rejection error before that.
 * Rules that follow the route must ignore rejected messages: the vehicle never saw them as its order.
 */
final class OrderLedger {

    enum Status { PENDING, ACCEPTED, REJECTED }

    static final class Sent {

        final OrderView order;
        Status status = Status.PENDING;

        Sent(OrderView order) {
            this.order = order;
        }
    }

    private final Map<String, List<Sent>> sent = new HashMap<>();

    /** Call for every message. */
    void observe(Message m) {
        if (!m.hasObjectBody()) {
            return;
        }
        if (m.topicName().equals("order")) {
            OrderView order = OrderView.of(m);
            if (order.orderId() != null && order.orderUpdateId() != null) {
                sent.computeIfAbsent(m.vehicle(), k -> new ArrayList<>()).add(new Sent(order));
            }
        } else if (m.topicName().equals("state")) {
            StateView state = StateView.of(m);
            for (Sent s : sent.getOrDefault(m.vehicle(), List.of())) {
                if (s.status != Status.PENDING) {
                    continue;
                }
                if (s.order.orderId().equals(state.orderId()) && state.orderUpdateId() != null
                        && state.orderUpdateId() >= s.order.orderUpdateId()) {
                    s.status = Status.ACCEPTED;
                } else if (rejects(state, s.order)) {
                    s.status = Status.REJECTED;
                }
            }
        }
    }

    private static boolean rejects(StateView state, OrderView order) {
        boolean referencesUpdate = state.errors().stream().anyMatch(e -> {
            String orderId = e.references().get("orderId");
            return (orderId == null || orderId.equals(order.orderId()))
                    && String.valueOf(order.orderUpdateId()).equals(e.references().get("orderUpdateId"));
        });
        boolean anyUpdateReference = state.errors().stream().anyMatch(e -> e.references().containsKey("orderUpdateId"));
        // Prefer exact references; without them fall back to the error type
        return referencesUpdate || !anyUpdateReference && state.hasError(OrderAckRule.REJECTIONS);
    }

    /** Messages of {@code orderId} the vehicle accepted, in the order they were sent. */
    List<OrderView> accepted(String vehicle, String orderId) {
        return sent.getOrDefault(vehicle, List.of()).stream()
                .filter(s -> s.status == Status.ACCEPTED && s.order.orderId().equals(orderId))
                .map(s -> s.order).toList();
    }

    /**
     * The message an update of {@code orderId} has to continue: the latest one not known to be
     * rejected, excluding {@code current} itself. {@code null} if there is none.
     */
    OrderView continued(String vehicle, String orderId, Message current) {
        List<Sent> list = sent.getOrDefault(vehicle, List.of());
        for (int i = list.size() - 1; i >= 0; i--) {
            Sent s = list.get(i);
            if (s.order.message() != current && s.order.orderId().equals(orderId) && s.status != Status.REJECTED) {
                return s.order;
            }
        }
        return null;
    }
}
