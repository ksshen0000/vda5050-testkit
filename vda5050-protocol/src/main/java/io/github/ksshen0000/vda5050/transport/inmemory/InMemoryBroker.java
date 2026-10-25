package io.github.ksshen0000.vda5050.transport.inmemory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ksshen0000.vda5050.transport.MessageHandler;
import io.github.ksshen0000.vda5050.transport.MqttTransport;
import io.github.ksshen0000.vda5050.transport.TopicFilters;
import io.github.ksshen0000.vda5050.transport.WillMessage;

/**
 * A process-local MQTT broker for tests and single-process simulation. Delivery is synchronous on the
 * publishing thread, which keeps tests deterministic. Supports retained messages and last wills.
 */
public final class InMemoryBroker {

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<String, byte[]> retained = new ConcurrentHashMap<>();

    public InMemoryTransport newClient() {
        return new InMemoryTransport(this);
    }

    /** Retained payload of a topic, or {@code null}. */
    public byte[] retainedMessage(String topic) {
        return retained.get(topic);
    }

    void publish(String topic, byte[] payload, boolean retain) {
        if (retain) {
            if (payload.length == 0) {
                retained.remove(topic);
            } else {
                retained.put(topic, payload);
            }
        }
        for (Subscription s : subscriptions) {
            if (s.client.isConnected() && TopicFilters.matches(s.filter, topic)) {
                s.handler.onMessage(topic, payload);
            }
        }
    }

    void subscribe(InMemoryTransport client, String filter, MessageHandler handler) {
        subscriptions.add(new Subscription(client, filter, handler));
        List<Map.Entry<String, byte[]>> matching = new ArrayList<>();
        retained.forEach((topic, payload) -> {
            if (TopicFilters.matches(filter, topic)) {
                matching.add(Map.entry(topic, payload));
            }
        });
        matching.forEach(e -> handler.onMessage(e.getKey(), e.getValue()));
    }

    void connectionLost(InMemoryTransport client, WillMessage will) {
        subscriptions.removeIf(s -> s.client == client);
        if (will != null) {
            publish(will.topic(), will.payload(), will.retained());
        }
    }

    void disconnected(InMemoryTransport client) {
        subscriptions.removeIf(s -> s.client == client);
    }

    private record Subscription(InMemoryTransport client, String filter, MessageHandler handler) {
    }
}
