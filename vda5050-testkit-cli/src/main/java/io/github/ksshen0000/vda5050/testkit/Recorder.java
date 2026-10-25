package io.github.ksshen0000.vda5050.testkit;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilder;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import io.github.ksshen0000.vda5050.conformance.TraceEntry;

/**
 * Subscribes to everything below the interface name and hands each message on as a {@link TraceEntry}.
 * With MQTT 5 the subscription uses "retain as published", so the retain flag of live messages is
 * known; with MQTT 3.1.1 only retained messages replayed on subscribe are marked.
 */
final class Recorder implements AutoCloseable {

    private final BrokerOptions broker;
    private final boolean mqtt3;
    private final Clock clock;
    private Mqtt5AsyncClient client5;
    private Mqtt3AsyncClient client3;

    Recorder(BrokerOptions broker, boolean mqtt3, Clock clock) {
        this.broker = broker;
        this.mqtt3 = mqtt3;
        this.clock = clock;
    }

    /** Connects, subscribes to {@code filter} and blocks until the broker acknowledged. */
    void start(String filter, Consumer<TraceEntry> sink) throws Exception {
        MqttClientBuilder builder = MqttClient.builder()
                .identifier("vda5050-testkit-" + UUID.randomUUID().toString().substring(0, 8))
                .serverHost(broker.host())
                .serverPort(broker.port())
                .automaticReconnectWithDefaultConfig();
        if (broker.tls()) {
            builder = builder.sslWithDefaultConfig();
        }
        Duration timeout = Duration.ofSeconds(10);
        if (mqtt3) {
            var b = builder.useMqttVersion3();
            if (broker.username != null) {
                b = b.simpleAuth().username(broker.username)
                        .password(broker.password == null ? new byte[0] : broker.password.getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth();
            }
            client3 = b.buildAsync();
            client3.connect().get(timeout.toSeconds(), TimeUnit.SECONDS);
            client3.subscribeWith().topicFilter(filter).qos(MqttQos.AT_LEAST_ONCE)
                    .callback(p -> sink.accept(new TraceEntry(clock.instant(), p.getTopic().toString(), p.getPayloadAsBytes(),
                            p.isRetain() ? Boolean.TRUE : null)))
                    .send().get(timeout.toSeconds(), TimeUnit.SECONDS);
        } else {
            var b = builder.useMqttVersion5();
            if (broker.username != null) {
                b = b.simpleAuth().username(broker.username)
                        .password(broker.password == null ? new byte[0] : broker.password.getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth();
            }
            client5 = b.buildAsync();
            client5.connect().get(timeout.toSeconds(), TimeUnit.SECONDS);
            client5.subscribeWith().topicFilter(filter).qos(MqttQos.AT_LEAST_ONCE).retainAsPublished(true)
                    .callback(p -> sink.accept(new TraceEntry(clock.instant(), p.getTopic().toString(), p.getPayloadAsBytes(),
                            p.isRetain())))
                    .send().get(timeout.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        try {
            if (client5 != null) {
                client5.disconnect().get(5, TimeUnit.SECONDS);
            }
            if (client3 != null) {
                client3.disconnect().get(5, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            // shutting down anyway
        }
    }
}
