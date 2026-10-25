package io.github.ksshen0000.vda5050.transport.hivemq;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5ConnectBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublish;
import io.github.ksshen0000.vda5050.transport.MessageHandler;
import io.github.ksshen0000.vda5050.transport.MqttTransport;
import io.github.ksshen0000.vda5050.transport.Qos;
import io.github.ksshen0000.vda5050.transport.WillMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT 5 transport on the HiveMQ client. Reconnects automatically; subscriptions are restored after a
 * reconnect and connected listeners run again (so the vehicle can re-announce ONLINE).
 */
public final class HiveMqTransport implements MqttTransport {

    private static final Logger log = LoggerFactory.getLogger(HiveMqTransport.class);

    private final String host;
    private final int port;
    private final String clientId;
    private final Duration timeout;
    private final List<Runnable> connectedListeners = new CopyOnWriteArrayList<>();
    private Mqtt5AsyncClient client;

    public HiveMqTransport(String host, int port, String clientId) {
        this(host, port, clientId, Duration.ofSeconds(10));
    }

    public HiveMqTransport(String host, int port, String clientId, Duration timeout) {
        this.host = host;
        this.port = port;
        this.clientId = clientId;
        this.timeout = timeout;
    }

    @Override
    public void addConnectedListener(Runnable listener) {
        if (client != null) {
            throw new IllegalStateException("listeners must be added before connect");
        }
        connectedListeners.add(listener);
    }

    @Override
    public void connect(WillMessage will) {
        client = MqttClient.builder()
                .useMqttVersion5()
                .identifier(clientId)
                .serverHost(host)
                .serverPort(port)
                .automaticReconnectWithDefaultConfig()
                .addConnectedListener(ctx -> connectedListeners.forEach(HiveMqTransport::runSafely))
                .addDisconnectedListener(ctx -> log.info("MQTT client {} disconnected: {}", clientId,
                        String.valueOf(ctx.getCause())))
                .buildAsync();
        Mqtt5ConnectBuilder connect = Mqtt5Connect.builder().cleanStart(true);
        if (will != null) {
            connect.willPublish(Mqtt5WillPublish.builder()
                    .topic(will.topic())
                    .payload(will.payload())
                    .qos(toMqtt(will.qos()))
                    .retain(will.retained())
                    .build());
        }
        await(client.connect(connect.build()), "connect to " + host + ":" + port);
    }

    @Override
    public void publish(String topic, byte[] payload, Qos qos, boolean retained) {
        Mqtt5AsyncClient c = requireStarted();
        if (!c.getState().isConnected()) {
            // The disconnect itself is logged once; dropping messages while offline is expected
            log.debug("dropping message to {}: not connected", topic);
            return;
        }
        c.publishWith()
                .topic(topic)
                .payload(payload)
                .qos(toMqtt(qos))
                .retain(retained)
                .send()
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.warn("publish to {} failed: {}", topic, error.getMessage());
                    }
                });
    }

    @Override
    public void subscribe(String topicFilter, Qos qos, MessageHandler handler) {
        await(requireStarted().subscribeWith()
                .topicFilter(topicFilter)
                .qos(toMqtt(qos))
                .callback(publish -> {
                    try {
                        handler.onMessage(publish.getTopic().toString(), publish.getPayloadAsBytes());
                    } catch (RuntimeException e) {
                        log.error("handler for {} failed", publish.getTopic(), e);
                    }
                })
                .send(), "subscribe to " + topicFilter);
    }

    @Override
    public void close() {
        if (client == null || !client.getState().isConnectedOrReconnect()) {
            return;
        }
        try {
            await(client.disconnect(), "disconnect");
        } catch (IllegalStateException e) {
            // Broker already gone (e.g. while reconnecting); nothing left to close cleanly
            log.debug("MQTT client {} could not disconnect cleanly: {}", clientId, e.getMessage());
        }
    }

    private Mqtt5AsyncClient requireStarted() {
        if (client == null) {
            throw new IllegalStateException("not connected");
        }
        return client;
    }

    private <T> T await(CompletableFuture<T> future, String what) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during " + what, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("MQTT " + what + " failed", e);
        }
    }

    private static MqttQos toMqtt(Qos qos) {
        return qos == Qos.AT_LEAST_ONCE ? MqttQos.AT_LEAST_ONCE : MqttQos.AT_MOST_ONCE;
    }

    private static void runSafely(Runnable listener) {
        try {
            listener.run();
        } catch (RuntimeException e) {
            log.error("connected listener failed", e);
        }
    }
}
