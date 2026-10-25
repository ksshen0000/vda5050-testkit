package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.HashMap;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;

/** Last {@code connectionState} per vehicle, as seen so far in the trace. Each rule keeps its own. */
final class ConnectionStates {

    private final Map<String, String> states = new HashMap<>();
    private final Map<String, Integer> generations = new HashMap<>();

    /** Call for every message, before evaluating it. */
    void observe(Message m) {
        if (!m.topicName().equals("connection") || !m.hasObjectBody()) {
            return;
        }
        String state = Json.text(m.body(), "connectionState");
        if (state == null) {
            return;
        }
        String previous = states.put(m.vehicle(), state);
        if ("ONLINE".equals(state) && !"ONLINE".equals(previous)) {
            generations.merge(m.vehicle(), 1, Integer::sum);
        }
    }

    /** Last state, or {@code null} if none seen yet. */
    String state(String vehicle) {
        return states.get(vehicle);
    }

    /** False only if the vehicle's last connection message said it is not online. */
    boolean maybeOnline(String vehicle) {
        String state = states.get(vehicle);
        return state == null || state.equals("ONLINE");
    }

    /** Increases each time the vehicle comes ONLINE again; tells reconnects apart. */
    int generation(String vehicle) {
        return generations.getOrDefault(vehicle, 0);
    }
}
