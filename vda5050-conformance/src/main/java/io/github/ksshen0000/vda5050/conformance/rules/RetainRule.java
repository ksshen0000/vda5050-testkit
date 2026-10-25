package io.github.ksshen0000.vda5050.conformance.rules;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/**
 * {@code connection} (and since 2.1 {@code factsheet}) are published retained. Orders and instant
 * actions should not be: a vehicle would receive them again after every reconnect. Needs a trace that
 * recorded the retain flag of live messages (MQTT 5 "retain as published").
 */
final class RetainRule extends Rule {

    @Override
    public String id() {
        return "RETAIN";
    }

    @Override
    public String title() {
        return "Retain flag";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 6.14, 6.15 / 3.0: 6.5, 6.10";
    }

    @Override
    public void onMessage(Message m) {
        Boolean retained = m.entry().retained();
        if (retained == null || m.isEmpty() || !m.wellFormedTopic()) {
            return;
        }
        String topic = m.topicName();
        if (m.dialect().retainedTopic(topic, m.protocolVersion())) {
            checked();
            if (!retained) {
                error(m.vehicle(), topic, "%s shall be published with the retain flag".formatted(topic), m);
            }
        } else if (topic.equals("order") || topic.equals("instantActions")) {
            checked();
            if (retained) {
                warning(m.vehicle(), topic, "%s is retained; the vehicle gets it again on every reconnect".formatted(topic), m);
            }
        }
    }
}
