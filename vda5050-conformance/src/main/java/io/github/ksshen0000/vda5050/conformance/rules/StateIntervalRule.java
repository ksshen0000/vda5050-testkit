package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/** While online, a vehicle publishes state on events and at the latest every 30 seconds. */
final class StateIntervalRule extends Rule {

    /** Allowance for broker and network delay on top of the interval. */
    private static final Duration SLACK = Duration.ofSeconds(1);

    private final Duration interval;
    private final Map<String, Message> lastState = new HashMap<>();

    StateIntervalRule(Duration interval) {
        this.interval = interval;
    }

    @Override
    public String id() {
        return "STATE-INTERVAL";
    }

    @Override
    public String title() {
        return "State at least every %d s".formatted(interval.toSeconds());
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.10 / 3.0: 6.6";
    }

    @Override
    public void onMessage(Message m) {
        if (m.topicName().equals("connection") && !m.isEmpty()) {
            if (!"ONLINE".equals(Json.text(m.body(), "connectionState"))) {
                // Not online (or hibernating): silence is expected until the next state
                lastState.remove(m.vehicle());
            }
            return;
        }
        if (!m.topicName().equals("state") || !m.hasObjectBody()) {
            return;
        }
        Message previous = lastState.put(m.vehicle(), m);
        if (previous == null) {
            return;
        }
        checked();
        Duration gap = Duration.between(previous.receivedAt(), m.receivedAt());
        if (gap.compareTo(interval.plus(SLACK)) > 0) {
            error(m.vehicle(), "gap", "no state for %.1f s; the limit is %d s".formatted(gap.toMillis() / 1000.0,
                    interval.toSeconds()), previous, m);
        }
    }

    @Override
    public void onEnd(Instant end) {
        lastState.forEach((vehicle, m) -> {
            Duration gap = Duration.between(m.receivedAt(), end);
            if (gap.compareTo(interval.plus(SLACK)) > 0) {
                checked();
                error(vehicle, "gap", "no state for the last %.1f s of the trace; the limit is %d s"
                        .formatted(gap.toMillis() / 1000.0, interval.toSeconds()), m);
            }
        });
    }
}
