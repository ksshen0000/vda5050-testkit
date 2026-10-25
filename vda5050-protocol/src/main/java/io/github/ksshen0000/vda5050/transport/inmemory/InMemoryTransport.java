package io.github.ksshen0000.vda5050.transport.inmemory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.ksshen0000.vda5050.transport.MessageHandler;
import io.github.ksshen0000.vda5050.transport.MqttTransport;
import io.github.ksshen0000.vda5050.transport.Qos;
import io.github.ksshen0000.vda5050.transport.WillMessage;

/** A client of {@link InMemoryBroker}. */
public final class InMemoryTransport implements MqttTransport {

    private final InMemoryBroker broker;
    private final List<Runnable> connectedListeners = new CopyOnWriteArrayList<>();
    private volatile boolean connected;
    private WillMessage will;

    InMemoryTransport(InMemoryBroker broker) {
        this.broker = broker;
    }

    @Override
    public void addConnectedListener(Runnable listener) {
        connectedListeners.add(listener);
    }

    @Override
    public void connect(WillMessage will) {
        this.will = will;
        this.connected = true;
        connectedListeners.forEach(Runnable::run);
    }

    @Override
    public void publish(String topic, byte[] payload, Qos qos, boolean retained) {
        requireConnected();
        broker.publish(topic, payload, retained);
    }

    @Override
    public void subscribe(String topicFilter, Qos qos, MessageHandler handler) {
        requireConnected();
        broker.subscribe(this, topicFilter, handler);
    }

    /** Simulates a network failure: the broker publishes the will. */
    public void simulateConnectionLoss() {
        connected = false;
        broker.connectionLost(this, will);
    }

    @Override
    public void close() {
        if (connected) {
            connected = false;
            broker.disconnected(this);
        }
    }

    boolean isConnected() {
        return connected;
    }

    private void requireConnected() {
        if (!connected) {
            throw new IllegalStateException("not connected");
        }
    }
}
