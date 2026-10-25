package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/**
 * {@code headerId} is counted per topic and goes up by one with every message sent. A gap can mean lost
 * messages (QoS 0) and is a warning; a repeated or lower id is an error unless the publisher reconnected
 * in between (a restarted vehicle may start counting again).
 */
final class HeaderIdRule extends Rule {

    private final ConnectionStates connections = new ConnectionStates();
    private final Map<String, Last> last = new HashMap<>();

    /** @param replayed a retained message the broker handed out on subscribe; it may be arbitrarily old */
    private record Last(long headerId, Message message, int generation, boolean replayed) {
    }

    @Override
    public String id() {
        return "HEADER-ID";
    }

    @Override
    public String title() {
        return "headerId increases by one per topic";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 6.4 / 3.0: 7.2";
    }

    @Override
    public void onMessage(Message m) {
        connections.observe(m);
        Long headerId = m.headerId();
        // The will's header is fixed at connect time, so it is outdated by design
        if (headerId == null || m.isLastWill()) {
            return;
        }
        String key = m.entry().topic();
        int generation = connections.generation(m.vehicle());
        Last previous = last.get(key);
        last.put(key, new Last(headerId, m, generation,
                previous == null && Boolean.TRUE.equals(m.entry().retained())));
        if (previous == null) {
            return;
        }
        checked();
        long expected = previous.headerId + 1;
        if (headerId == expected) {
            return;
        }
        if (headerId == previous.headerId && m.topicName().equals("connection")
                && Arrays.equals(m.entry().payload(), previous.message.entry().payload())) {
            // QoS 1 may deliver a message twice
            return;
        }
        if (headerId > expected) {
            if (m.topicName().equals("connection")) {
                // Each connect sets a will with the next id; it is only published if the connection breaks
                return;
            }
            warning(m.vehicle(), m.topicName() + " gap", "%s: headerId jumped from %d to %d; %d message(s) lost or never sent"
                    .formatted(m.topicName(), previous.headerId, headerId, headerId - expected), previous.message, m);
        } else if (previous.replayed || generation != previous.generation) {
            warning(m.vehicle(), m.topicName() + " restart", "%s: headerId restarted at %d after %d across a reconnect"
                    .formatted(m.topicName(), headerId, previous.headerId), previous.message, m);
        } else {
            error(m.vehicle(), m.topicName() + " order", "%s: headerId %d follows %d; it shall increase by one"
                    .formatted(m.topicName(), headerId, previous.headerId), previous.message, m);
        }
    }
}
