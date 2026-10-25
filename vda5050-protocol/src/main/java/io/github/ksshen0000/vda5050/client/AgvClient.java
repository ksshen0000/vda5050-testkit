package io.github.ksshen0000.vda5050.client;

import java.time.Clock;
import java.util.Objects;
import java.util.function.Consumer;

import io.github.ksshen0000.vda5050.transport.MqttTransport;
import io.github.ksshen0000.vda5050.transport.WillMessage;
import io.github.ksshen0000.vda5050.v2.Connection;
import io.github.ksshen0000.vda5050.v2.ConnectionState;
import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.InstantActions;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.State;
import io.github.ksshen0000.vda5050.v2.TopicPath;
import io.github.ksshen0000.vda5050.v2.Vda5050Codec;
import io.github.ksshen0000.vda5050.v2.Vda5050Message;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;
import io.github.ksshen0000.vda5050.v2.Visualization;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The vehicle side of VDA 5050: receives orders and instant actions, publishes state, visualization,
 * connection and factsheet. Announces ONLINE on every (re)connect and registers CONNECTIONBROKEN as
 * last will.
 */
public final class AgvClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AgvClient.class);

    private final MqttTransport transport;
    private final TopicPath base;
    private final Vda5050Codec codec = new Vda5050Codec();
    private final HeaderSequence headers;

    public AgvClient(MqttTransport transport, String interfaceName, String manufacturer, String serialNumber, Clock clock) {
        this.transport = transport;
        this.base = new TopicPath(interfaceName, TopicPath.MAJOR_VERSION, manufacturer, serialNumber, Vda5050Topic.STATE);
        this.headers = new HeaderSequence(clock);
    }

    public void start(Consumer<Order> onOrder, Consumer<InstantActions> onInstantActions) {
        Objects.requireNonNull(onOrder);
        Objects.requireNonNull(onInstantActions);
        transport.addConnectedListener(() -> publish(Vda5050Topic.CONNECTION,
                new Connection(Header.UNSTAMPED, ConnectionState.ONLINE)));
        transport.connect(connectionWill());
        subscribe(Vda5050Topic.ORDER, Order.class, onOrder);
        subscribe(Vda5050Topic.INSTANT_ACTIONS, InstantActions.class, onInstantActions);
    }

    public void publishState(State state) {
        publish(Vda5050Topic.STATE, state);
    }

    public void publishVisualization(Visualization visualization) {
        publish(Vda5050Topic.VISUALIZATION, visualization);
    }

    public void publishFactsheet(Factsheet factsheet) {
        publish(Vda5050Topic.FACTSHEET, factsheet);
    }

    public String serialNumber() {
        return base.serialNumber();
    }

    /** Announces OFFLINE and disconnects cleanly (the broker discards the will). */
    @Override
    public void close() {
        try {
            publish(Vda5050Topic.CONNECTION, new Connection(Header.UNSTAMPED, ConnectionState.OFFLINE));
        } catch (RuntimeException e) {
            log.warn("could not announce OFFLINE for {}: {}", base.serialNumber(), e.getMessage());
        }
        transport.close();
    }

    private WillMessage connectionWill() {
        // The will's header is fixed at connect time; its headerId is taken from the connection counter.
        Connection broken = new Connection(Header.UNSTAMPED, ConnectionState.CONNECTIONBROKEN)
                .withHeader(headers.next(base.manufacturer(), base.serialNumber(), Vda5050Topic.CONNECTION));
        Vda5050Topic topic = Vda5050Topic.CONNECTION;
        return new WillMessage(base.withTopic(topic).toString(), codec.encode(broken), topic.qos(), topic.retained());
    }

    private void publish(Vda5050Topic topic, Vda5050Message message) {
        Vda5050Message stamped = message.withHeader(headers.next(base.manufacturer(), base.serialNumber(), topic));
        transport.publish(base.withTopic(topic).toString(), codec.encode(stamped), topic.qos(), topic.retained());
    }

    private <T extends Vda5050Message> void subscribe(Vda5050Topic topic, Class<T> type, Consumer<T> consumer) {
        transport.subscribe(base.withTopic(topic).toString(), topic.qos(), (t, payload) -> {
            T message;
            try {
                message = codec.decode(payload, type);
            } catch (Vda5050Codec.Vda5050DecodingException e) {
                log.warn("{}: dropping malformed {} message: {}", base.serialNumber(), topic.segment(), e.getMessage());
                return;
            }
            consumer.accept(message);
        });
    }
}
