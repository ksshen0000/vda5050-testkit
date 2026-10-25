package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.ActionView;
import io.github.ksshen0000.vda5050.conformance.rules.StateView.ActionStatus;

/**
 * {@code cancelOrder}: with an active order the vehicle stops, fails the order's remaining actions,
 * empties {@code nodeStates} / {@code edgeStates}, keeps {@code orderId} and then reports the cancel
 * FINISHED. Without an active order the cancel FAILS with a {@code noOrderToCancel} warning.
 */
final class CancelOrderRule extends Rule {

    private static final Set<String> NO_ORDER = Set.of("noOrderToCancel", "NO_ORDER_TO_CANCEL");

    private final Duration cancelTime;
    private final Map<String, StateView> lastState = new HashMap<>();
    private final Map<String, List<Pending>> pending = new HashMap<>();
    /** Ids of instant actions sent to each vehicle; in 2.x they share actionStates with order actions. */
    private final Map<String, Set<String>> instantIds = new HashMap<>();

    /**
     * @param active  whether the vehicle had an order to cancel when the cancel was sent
     * @param orderId the order at that time
     */
    private record Pending(ActionView action, Message source, boolean active, String orderId, Instant deadline) {
    }

    CancelOrderRule(Duration cancelTime) {
        this.cancelTime = cancelTime;
    }

    @Override
    public String id() {
        return "CANCEL-ORDER";
    }

    @Override
    public String title() {
        return "cancelOrder is carried out as specified";
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.6.3 / 3.0: 6.1.3, 6.6.7";
    }

    @Override
    public void onMessage(Message m) {
        String v = m.vehicle();
        if (m.topicName().equals("instantActions") && m.hasObjectBody()) {
            for (ActionView a : OrderView.actions(m.body())) {
                if (a.actionId() != null) {
                    instantIds.computeIfAbsent(v, k -> new HashSet<>()).add(a.actionId());
                }
                if (!"cancelOrder".equals(a.actionType()) || a.actionId() == null) {
                    continue;
                }
                StateView state = lastState.get(v);
                if (state == null) {
                    inconclusive("%s: cancelOrder %s sent before any state was seen".formatted(v, a.actionId()));
                    continue;
                }
                pending.computeIfAbsent(v, k -> new ArrayList<>()).add(new Pending(a, m, active(v, state),
                        state.orderId(), m.receivedAt().plus(cancelTime)));
            }
            return;
        }
        if (m.topicName().equals("order")) {
            // A new order ends what the cancel was about; judging it further would be guesswork
            List<Pending> open = pending.remove(v);
            if (open != null) {
                open.forEach(p -> inconclusive("%s: new order sent before cancelOrder %s completed".formatted(v, p.action().actionId())));
            }
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
            ActionStatus status = state.action(p.action().actionId());
            String id = p.action().actionId();
            if (status != null && status.terminal()) {
                checked();
                it.remove();
                if (p.active()) {
                    checkCancelled(v, m, state, p, status);
                } else {
                    checkNothingToCancel(v, m, state, p, status);
                }
            } else if (m.receivedAt().isAfter(p.deadline())) {
                checked();
                it.remove();
                error(v, "not finished", "cancelOrder %s is still %s after %d s".formatted(id,
                        status == null ? "not reported" : status.status(), cancelTime.toSeconds()), p.source(), m);
            }
        }
    }

    private void checkCancelled(String v, Message m, StateView state, Pending p, ActionStatus status) {
        String id = p.action().actionId();
        if (!status.status().equals("FINISHED")) {
            error(v, "failed with order", "cancelOrder %s FAILED although order %s was active".formatted(id, p.orderId()), p.source(), m);
            return;
        }
        if (!state.nodeStates().isEmpty() || !state.edgeStates().isEmpty()) {
            error(v, "states kept", "cancelOrder %s FINISHED but nodeStates/edgeStates still list %d/%d entries"
                    .formatted(id, state.nodeStates().size(), state.edgeStates().size()), p.source(), m);
        }
        if (!Objects.equals(state.orderId(), p.orderId())) {
            error(v, "orderId changed", "after cancelOrder %s the orderId is %s; it shall stay %s"
                    .formatted(id, state.orderId(), p.orderId()), p.source(), m);
        }
        for (ActionStatus a : state.actionStates().values()) {
            if (!a.terminal() && !isInstant(v, state, a)) {
                error(v, "actions left", "cancelOrder %s FINISHED while order action %s is still %s"
                        .formatted(id, a.actionId(), a.status()), p.source(), m);
            }
        }
    }

    private void checkNothingToCancel(String v, Message m, StateView state, Pending p, ActionStatus status) {
        String id = p.action().actionId();
        if (!status.status().equals("FAILED")) {
            error(v, "finished without order", "cancelOrder %s is %s although there was no active order; it shall be FAILED"
                    .formatted(id, status.status()), p.source(), m);
            return;
        }
        var noOrder = state.errors().stream().filter(e -> NO_ORDER.contains(e.errorType())).findFirst();
        if (noOrder.isEmpty()) {
            error(v, "no warning", "cancelOrder %s FAILED without the noOrderToCancel warning (3.0: NO_ORDER_TO_CANCEL)"
                    .formatted(id), p.source(), m);
        } else {
            if (!"WARNING".equals(noOrder.get().errorLevel())) {
                warning(v, "level", "noOrderToCancel has errorLevel %s; it shall be WARNING".formatted(noOrder.get().errorLevel()), m);
            }
            if (!id.equals(noOrder.get().references().get("actionId"))) {
                warning(v, "reference", "noOrderToCancel does not reference actionId %s".formatted(id), m);
            }
        }
    }

    /** Has nodes or edges left, or order actions that are not finished. */
    private boolean active(String v, StateView state) {
        return !state.nodeStates().isEmpty() || !state.edgeStates().isEmpty()
                || state.actionStates().values().stream().anyMatch(a -> !a.terminal() && !isInstant(v, state, a));
    }

    private boolean isInstant(String v, StateView state, ActionStatus a) {
        return state.instantActionStates().containsKey(a.actionId())
                || instantIds.getOrDefault(v, Set.of()).contains(a.actionId());
    }

    @Override
    public void onEnd(Instant end) {
        pending.forEach((v, open) -> open.forEach(p -> {
            if (end.isAfter(p.deadline())) {
                checked();
                error(v, "not finished", "no state reports cancelOrder %s as finished or failed".formatted(p.action().actionId()), p.source());
            } else {
                inconclusive("%s: trace ends before cancelOrder %s completed".formatted(v, p.action().actionId()));
            }
        }));
    }
}
