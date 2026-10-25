package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/**
 * A vehicle announces {@code ONLINE} on the connection topic when it connects, before it reports state,
 * and announces {@code OFFLINE} before a graceful disconnect. A vehicle that keeps publishing while its
 * last connection message says it is gone forgot to announce ONLINE again after reconnecting.
 */
final class ConnectionRule extends Rule {

    private final ConnectionStates connections = new ConnectionStates();
    /** First state of every vehicle that publishes state, to report vehicles that never sent connection. */
    private final Map<String, Message> firstState = new LinkedHashMap<>();

    @Override
    public String id() {
        return "CONNECTION";
    }

    @Override
    public String title() {
        return "Connection state is announced";
    }

    @Override
    public Party party() {
        return Party.VEHICLE;
    }

    @Override
    public String section() {
        return "2.x: 6.14 / 3.0: 6.5";
    }

    @Override
    public void onMessage(Message m) {
        connections.observe(m);
        if (!m.hasObjectBody() || !(m.topicName().equals("state") || m.topicName().equals("visualization"))) {
            return;
        }
        String vehicle = m.vehicle();
        firstState.putIfAbsent(vehicle, m);
        String state = connections.state(vehicle);
        if (state == null) {
            return; // decided at the end: the retained message may simply have been cleared
        }
        checked();
        if (!state.equals("ONLINE")) {
            error(vehicle, "publishing while " + state, "%s published while the last connection message says %s; ONLINE shall be announced on (re)connect"
                    .formatted(m.topicName(), state), m);
        }
    }

    @Override
    public void onEnd(Instant end) {
        firstState.forEach((vehicle, m) -> {
            if (connections.state(vehicle) == null) {
                checked();
                error(vehicle, "missing", "publishes state but no connection message was seen; ONLINE shall be published retained when connecting", m);
            }
        });
    }
}
