package io.github.ksshen0000.vda5050.transport;

@FunctionalInterface
public interface MessageHandler {

    void onMessage(String topic, byte[] payload);
}
