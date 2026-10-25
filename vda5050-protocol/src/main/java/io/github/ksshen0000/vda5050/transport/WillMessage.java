package io.github.ksshen0000.vda5050.transport;

/** Message the broker publishes on the client's behalf when the connection is lost without a clean disconnect. */
public record WillMessage(String topic, byte[] payload, Qos qos, boolean retained) {
}
