package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.Dialect;
import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.rules.OrderView.ActionView;
import io.github.ksshen0000.vda5050.conformance.rules.StateView.ActionStatus;

/**
 * Every action of an accepted order and every instant action shows up in the state, and its
 * {@code actionStatus} only moves forward: WAITING, INITIALIZING, RUNNING (and PAUSED, 3.0 RETRIABLE)
 * towards FINISHED or FAILED, which are final.
 */
final class ActionStatesRule extends Rule {

    private static final Map<String, Set<String>> NEXT = Map.of(
            "WAITING", Set.of("INITIALIZING", "RUNNING", "PAUSED", "FINISHED", "FAILED"),
            "INITIALIZING", Set.of("RUNNING", "PAUSED", "RETRIABLE", "FINISHED", "FAILED"),
            "RUNNING", Set.of("PAUSED", "RETRIABLE", "FINISHED", "FAILED"),
            "PAUSED", Set.of("INITIALIZING", "RUNNING", "RETRIABLE", "FINISHED", "FAILED"),
            "RETRIABLE", Set.of("INITIALIZING", "RUNNING", "FINISHED", "FAILED"),
            "FINISHED", Set.of(),
            "FAILED", Set.of());

    private final Duration reactionTime;
    private final OrderLedger ledger = new OrderLedger();
    private final Map<String, List<Expected>> expected = new HashMap<>();
    private final Map<String, Map<String, ActionStatus>> lastStatus = new HashMap<>();
    private final Map<String, StateView> previous = new HashMap<>();

    /** @param instant from an instantActions message, else from an order */
    private record Expected(ActionView action, Message source, Instant deadline, boolean instant) {
    }

    ActionStatesRule(Duration reactionTime) {
        this.reactionTime = reactionTime;
    }

    @Override
    public String id() {
        return "ACTION-STATES";
    }

    @Override
    public String title() {
        return "Actions are reported and progress forward";
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.9, 6.11 (figure 16) / 3.0: 6.2.1, 6.6.9";
    }

    @Override
    public void onMessage(Message m) {
        String v = m.vehicle();
        ledger.observe(m);
        switch (m.topicName()) {
            case "instantActions" -> {
                if (m.hasObjectBody()) {
                    List<Expected> list = expected.computeIfAbsent(v, k -> new ArrayList<>());
                    for (ActionView a : OrderView.actions(m.body())) {
                        if (a.actionId() != null) {
                            list.add(new Expected(a, m, m.receivedAt().plus(reactionTime), true));
                        }
                    }
                }
            }
            case "state" -> {
                if (m.hasObjectBody()) {
                    onState(v, m, StateView.of(m));
                }
            }
            default -> {
            }
        }
    }

    private void onState(String v, Message m, StateView state) {
        StateView before = previous.put(v, state);
        boolean newlyShown = state.orderId() != null && state.orderUpdateId() != null && (before == null
                || !Objects.equals(before.orderId(), state.orderId())
                || !Objects.equals(before.orderUpdateId(), state.orderUpdateId()));
        if (newlyShown && before != null) {
            // The order (update) was just accepted: its actions are due in the state
            long since = Objects.equals(before.orderId(), state.orderId()) && before.orderUpdateId() != null
                    ? before.orderUpdateId() : Long.MIN_VALUE;
            for (OrderView order : ledger.accepted(v, state.orderId())) {
                if (order.orderUpdateId() != null && order.orderUpdateId() > since && order.orderUpdateId() <= state.orderUpdateId()) {
                    for (ActionView a : order.allActions()) {
                        if (a.actionId() != null) {
                            expected.computeIfAbsent(v, k -> new ArrayList<>())
                                    .add(new Expected(a, order.message(), m.receivedAt().plus(reactionTime), false));
                        }
                    }
                }
            }
        }
        checkExpected(v, m, state);
        checkTransitions(v, m, state);
    }

    private void checkExpected(String v, Message m, StateView state) {
        List<Expected> open = expected.get(v);
        if (open == null) {
            return;
        }
        for (Iterator<Expected> it = open.iterator(); it.hasNext(); ) {
            Expected e = it.next();
            String id = e.action().actionId();
            ActionStatus status = state.action(id);
            if (status != null) {
                checked();
                it.remove();
                if (e.instant() && m.dialect() == Dialect.V3 && !state.instantActionStates().containsKey(id)) {
                    warning(v, "instant in actionStates", "instant action %s is reported in actionStates; 3.0 uses instantActionStates"
                            .formatted(id), m);
                }
            } else if (state.errorReferences("actionId", id)) {
                checked();
                it.remove(); // rejected with a reference to it
            } else if (m.receivedAt().isAfter(e.deadline())) {
                checked();
                it.remove();
                error(v, "missing", "%s action %s (%s) does not appear in the state within %.1f s"
                        .formatted(e.instant() ? "instant" : "order", id, e.action().actionType(),
                                reactionTime.toMillis() / 1000.0), e.source(), m);
            }
        }
    }

    private void checkTransitions(String v, Message m, StateView state) {
        Map<String, ActionStatus> last = lastStatus.computeIfAbsent(v, k -> new HashMap<>());
        List<ActionStatus> all = new ArrayList<>(state.actionStates().values());
        all.addAll(state.instantActionStates().values());
        for (ActionStatus now : all) {
            if (now.status() == null) {
                continue;
            }
            ActionStatus before = last.put(now.actionId(), now);
            if (before == null || before.status() == null || before.status().equals(now.status())) {
                continue;
            }
            checked();
            Set<String> allowed = NEXT.get(before.status());
            if (allowed != null && !allowed.contains(now.status())) {
                error(v, "transition", "action %s went from %s to %s".formatted(now.actionId(), before.status(), now.status()), m);
            }
        }
    }

    @Override
    public void onEnd(Instant end) {
        expected.forEach((v, open) -> open.forEach(e -> inconclusive("%s: trace ends before action %s was reported"
                .formatted(v, e.action().actionId()))));
    }
}
