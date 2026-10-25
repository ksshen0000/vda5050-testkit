package io.github.ksshen0000.vda5050.conformance.rules;

import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/** Every payload is a JSON object; only retained topics may see an empty payload (clearing them). */
final class PayloadRule extends Rule {

    @Override
    public String id() {
        return "PAYLOAD";
    }

    @Override
    public String title() {
        return "Payload is a JSON object";
    }

    @Override
    public Party party() {
        return Party.PUBLISHER;
    }

    @Override
    public String section() {
        return "2.x: 4 / 3.0: 4";
    }

    @Override
    public void onMessage(Message m) {
        checked();
        if (m.isEmpty()) {
            if (!m.dialect().retainedTopic(m.topicName(), null)) {
                error(m.vehicle(), "empty", "empty payload on %s".formatted(m.topicName()), m);
            }
            return;
        }
        if (m.body() == null) {
            error(m.vehicle(), "not json", "payload is not JSON: " + m.parseError(), m);
        } else if (!m.body().isObject()) {
            error(m.vehicle(), "not object", "payload is JSON but not an object", m);
        }
    }
}
