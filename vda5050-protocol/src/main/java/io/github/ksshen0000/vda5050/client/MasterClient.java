package io.github.ksshen0000.vda5050.client;

import java.time.Clock;
import java.util.function.BiConsumer;

import io.github.ksshen0000.vda5050.transport.MqttTransport;
import io.github.ksshen0000.vda5050.v2.Connection;
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
 * The fleet-control side of VDA 5050: sends orders and instant actions to any vehicle and receives
 * the messages of all vehicles through wildcard subscriptions.
 */
public final class MasterClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MasterClient.class);

    private final MqttTransport transport;
    private final String interfaceName;
    private final Vda5050Codec codec = new Vda5050Codec();
    private final HeaderSequence headers;
    private final MessageTap tap;

    public MasterClient(MqttTransport transport, String interfaceName, Clock clock) {
        this(transport, interfaceName, clock, MessageTap.NONE);
    }

    public MasterClient(MqttTransport transport, String interfaceName, Clock clock, MessageTap tap) {
        this.transport = transport;
        this.interfaceName = interfaceName;
        this.headers = new HeaderSequence(clock);
        this.tap = tap;
    }

    public void start(MasterListener listener) {
        transport.connect(null);
        subscribe(Vda5050Topic.CONNECTION, Connection.class, listener::onConnection);
        subscribe(Vda5050Topic.STATE, State.class, listener::onState);
        subscribe(Vda5050Topic.VISUALIZATION, Visualization.class, listener::onVisualization);
        subscribe(Vda5050Topic.FACTSHEET, Factsheet.class, listener::onFactsheet);
    }

    /** Sends an order; its header is assigned here. */
    public void sendOrder(VehicleId vehicle, Order order) {
        publish(vehicle, Vda5050Topic.ORDER, order);
    }

    public void sendInstantActions(VehicleId vehicle, InstantActions actions) {
        publish(vehicle, Vda5050Topic.INSTANT_ACTIONS, actions);
    }

    /**
     * Removes what the broker retains for a vehicle (its last connection state), so that a vehicle
     * taken out of service is not rediscovered from it.
     */
    public void clearRetained(VehicleId vehicle) {
        for (Vda5050Topic topic : Vda5050Topic.values()) {
            if (topic.retained()) {
                TopicPath path = new TopicPath(interfaceName, TopicPath.MAJOR_VERSION, vehicle.manufacturer(),
                        vehicle.serialNumber(), topic);
                transport.publish(path.toString(), new byte[0], topic.qos(), true);
            }
        }
    }

    @Override
    public void close() {
        transport.close();
    }

    private void publish(VehicleId vehicle, Vda5050Topic topic, Vda5050Message message) {
        Vda5050Message stamped = message.withHeader(headers.next(vehicle.manufacturer(), vehicle.serialNumber(), topic));
        TopicPath path = new TopicPath(interfaceName, TopicPath.MAJOR_VERSION, vehicle.manufacturer(),
                vehicle.serialNumber(), topic);
        byte[] payload = codec.encode(stamped);
        transport.publish(path.toString(), payload, topic.qos(), topic.retained());
        tap(() -> tap.sent(vehicle, topic, stamped, payload));
    }

    private <T extends Vda5050Message> void subscribe(Vda5050Topic topic, Class<T> type, BiConsumer<VehicleId, T> consumer) {
        transport.subscribe(TopicPath.filterForAllVehicles(interfaceName, topic), topic.qos(), (t, payload) -> {
            if (payload.length == 0) {
                // a retained message being cleared
                return;
            }
            TopicPath path;
            T message;
            try {
                path = TopicPath.parse(t);
                message = codec.decode(payload, type);
            } catch (IllegalArgumentException e) {
                log.warn("dropping malformed message on {}: {}", t, e.getMessage());
                return;
            }
            VehicleId vehicle = new VehicleId(path.manufacturer(), path.serialNumber());
            T decoded = message;
            tap(() -> tap.received(vehicle, topic, decoded, payload));
            consumer.accept(vehicle, message);
        });
    }

    private static void tap(Runnable call) {
        try {
            call.run();
        } catch (RuntimeException e) {
            log.warn("message tap failed", e);
        }
    }
}
