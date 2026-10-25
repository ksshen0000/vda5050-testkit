package io.github.ksshen0000.vda5050.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.client.AgvClient;
import io.github.ksshen0000.vda5050.client.MasterClient;
import io.github.ksshen0000.vda5050.client.MasterListener;
import io.github.ksshen0000.vda5050.client.VehicleId;
import io.github.ksshen0000.vda5050.transport.hivemq.HiveMqTransport;
import io.github.ksshen0000.vda5050.v2.Connection;
import io.github.ksshen0000.vda5050.v2.ConnectionState;
import io.github.ksshen0000.vda5050.v2.Order;
import io.github.ksshen0000.vda5050.v2.SampleMessages;
import io.github.ksshen0000.vda5050.v2.State;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** VDA 5050 clients over a real Mosquitto broker. Skipped when Docker is not available. */
@Testcontainers(disabledWithoutDocker = true)
class HiveMqTransportIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> MOSQUITTO = new GenericContainer<>("eclipse-mosquitto:2")
            .withCopyToContainer(Transferable.of("listener 1883\nallow_anonymous true\n"),
                    "/mosquitto/config/mosquitto.conf")
            .withExposedPorts(1883);

    private static final VehicleId AGV = new VehicleId("TESTKIT-SIM", "IT-001");

    @Test
    void orderStateAndConnectionLifecycleOverMqtt() {
        List<Connection> connections = new CopyOnWriteArrayList<>();
        List<State> states = new CopyOnWriteArrayList<>();
        List<Order> orders = new CopyOnWriteArrayList<>();

        MasterClient fleet = new MasterClient(transport("fleet"), "uagv", Clock.systemUTC());
        fleet.start(new MasterListener() {
            @Override
            public void onConnection(VehicleId vehicle, Connection connection) {
                connections.add(connection);
            }

            @Override
            public void onState(VehicleId vehicle, State state) {
                states.add(state);
            }
        });

        AgvClient agv = new AgvClient(transport("agv"), "uagv", AGV.manufacturer(), AGV.serialNumber(), Clock.systemUTC());
        agv.start(orders::add, ia -> { });

        await().atMost(Duration.ofSeconds(5)).until(() -> lastState(connections) == ConnectionState.ONLINE);

        fleet.sendOrder(AGV, SampleMessages.order());
        await().atMost(Duration.ofSeconds(5)).until(() -> orders.size() == 1);
        assertThat(orders.getFirst().nodes()).isEqualTo(SampleMessages.order().nodes());

        agv.publishState(SampleMessages.state());
        await().atMost(Duration.ofSeconds(5)).until(() -> states.size() == 1);

        agv.close();
        await().atMost(Duration.ofSeconds(5)).until(() -> lastState(connections) == ConnectionState.OFFLINE);
        fleet.close();
    }

    @Test
    void brokerPublishesConnectionBrokenWhenTheVehicleVanishes() {
        List<Connection> connections = new CopyOnWriteArrayList<>();
        MasterClient fleet = new MasterClient(transport("fleet-2"), "uagv", Clock.systemUTC());
        fleet.start(new MasterListener() {
            @Override
            public void onConnection(VehicleId vehicle, Connection connection) {
                if (vehicle.serialNumber().equals("IT-002")) {
                    connections.add(connection);
                }
            }
        });

        // A raw client that connects with the will and then drops the TCP connection without DISCONNECT
        com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient raw = com.hivemq.client.mqtt.MqttClient.builder()
                .useMqttVersion5()
                .identifier("agv-raw")
                .serverHost(MOSQUITTO.getHost())
                .serverPort(MOSQUITTO.getMappedPort(1883))
                .buildBlocking();
        raw.connectWith()
                .willPublish()
                .topic("uagv/v2/TESTKIT-SIM/IT-002/connection")
                .payload(("{\"headerId\":0,\"timestamp\":\"2026-10-18T08:00:00Z\",\"version\":\"2.1.0\","
                        + "\"manufacturer\":\"TESTKIT-SIM\",\"serialNumber\":\"IT-002\",\"connectionState\":\"CONNECTIONBROKEN\"}")
                        .getBytes())
                .qos(com.hivemq.client.mqtt.datatypes.MqttQos.AT_LEAST_ONCE)
                .retain(true)
                .applyWillPublish()
                .send();
        raw.disconnectWith()
                .reasonCode(com.hivemq.client.mqtt.mqtt5.message.disconnect.Mqtt5DisconnectReasonCode.DISCONNECT_WITH_WILL_MESSAGE)
                .send();

        await().atMost(Duration.ofSeconds(5)).until(() -> lastState(connections) == ConnectionState.CONNECTIONBROKEN);
        fleet.close();
    }

    private static HiveMqTransport transport(String clientId) {
        return new HiveMqTransport(MOSQUITTO.getHost(), MOSQUITTO.getMappedPort(1883), clientId);
    }

    private static ConnectionState lastState(List<Connection> connections) {
        return connections.isEmpty() ? null : connections.getLast().connectionState();
    }
}
