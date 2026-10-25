package io.github.ksshen0000.vda5050.transport;

/**
 * Minimal MQTT operations the VDA 5050 clients need, so that tests and the simulator can run on an
 * in-memory broker and production code on a real one.
 */
public interface MqttTransport extends AutoCloseable {

    /**
     * Registers a callback invoked after every successful (re)connect, before {@link #connect} returns
     * for the first connection. Must be called before {@link #connect}.
     */
    void addConnectedListener(Runnable listener);

    /**
     * Connects to the broker and blocks until connected.
     *
     * @param will last will, or {@code null}
     */
    void connect(WillMessage will);

    void publish(String topic, byte[] payload, Qos qos, boolean retained);

    /** Subscribes and blocks until the broker acknowledged. Subscriptions survive reconnects. */
    void subscribe(String topicFilter, Qos qos, MessageHandler handler);

    /** Disconnects cleanly: the will is discarded. */
    @Override
    void close();
}
