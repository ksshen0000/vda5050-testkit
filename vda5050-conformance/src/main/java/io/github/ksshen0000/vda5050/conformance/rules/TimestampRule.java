package io.github.ksshen0000.vda5050.conformance.rules;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import io.github.ksshen0000.vda5050.conformance.Rule;

/**
 * {@code timestamp} is ISO 8601 in UTC ({@code YYYY-MM-DDTHH:mm:ss.ffZ}), does not run backwards on a
 * topic, and is close to the time the observer received the message.
 */
final class TimestampRule extends Rule {

    private static final Pattern FORMAT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");

    private final Duration maxSkew;
    private final Map<String, Instant> lastPerTopic = new HashMap<>();
    private final Map<String, Message> lastMessage = new HashMap<>();

    TimestampRule(Duration maxSkew) {
        this.maxSkew = maxSkew;
    }

    @Override
    public String id() {
        return "TIMESTAMP";
    }

    @Override
    public String title() {
        return "Timestamps are UTC, ordered and in sync";
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
        if (!m.hasObjectBody()) {
            return;
        }
        String text = Json.text(m.body(), "timestamp");
        if (text == null) {
            return; // missing or not a string: the schema check reports it
        }
        checked();
        if (!FORMAT.matcher(text).matches()) {
            error(m.vehicle(), "format", "timestamp '%s' is not ISO 8601 UTC like 2017-04-15T11:40:03.12Z".formatted(text), m);
            return;
        }
        Instant timestamp;
        try {
            timestamp = Instant.parse(text);
        } catch (DateTimeParseException e) {
            error(m.vehicle(), "format", "timestamp '%s' is not a valid date and time".formatted(text), m);
            return;
        }
        // Wills and replayed retained messages carry old timestamps by design
        boolean stale = m.isLastWill() || Boolean.TRUE.equals(m.entry().retained()) && !lastPerTopic.containsKey(m.entry().topic());
        if (stale) {
            return;
        }
        Instant previous = lastPerTopic.put(m.entry().topic(), timestamp);
        Message previousMessage = lastMessage.put(m.entry().topic(), m);
        if (previous != null && timestamp.isBefore(previous)) {
            warning(m.vehicle(), m.topicName() + " backwards", "%s: timestamp %s is earlier than the previous one, %s"
                    .formatted(m.topicName(), text, previous), previousMessage, m);
        }
        Duration skew = Duration.between(m.receivedAt(), timestamp).abs();
        if (skew.compareTo(maxSkew) > 0) {
            warning(m.vehicle(), "clock skew", "%s: timestamp is %.1f s %s the receive time; is the clock synchronized?"
                    .formatted(m.topicName(), skew.toMillis() / 1000.0,
                            timestamp.isBefore(m.receivedAt()) ? "before" : "after"), m);
        }
    }
}
