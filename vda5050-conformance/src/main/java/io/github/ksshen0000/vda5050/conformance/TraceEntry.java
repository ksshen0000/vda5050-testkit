package io.github.ksshen0000.vda5050.conformance;

import java.time.Instant;
import java.util.Objects;

/**
 * One MQTT message as an observer on the broker saw it.
 *
 * @param receivedAt when the observer received it
 * @param topic      full MQTT topic
 * @param payload    raw payload bytes; empty for a cleared retained message
 * @param retained   the MQTT retain flag, or {@code null} if the recorder could not see it. Only an MQTT 5
 *                   subscription with "retain as published" reports the flag of live messages.
 */
public record TraceEntry(Instant receivedAt, String topic, byte[] payload, Boolean retained) {

    public TraceEntry {
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(topic, "topic");
        payload = payload == null ? new byte[0] : payload;
    }
}
