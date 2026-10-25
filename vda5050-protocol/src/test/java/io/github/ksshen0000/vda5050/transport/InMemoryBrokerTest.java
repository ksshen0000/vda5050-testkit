package io.github.ksshen0000.vda5050.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryBroker;
import io.github.ksshen0000.vda5050.transport.inmemory.InMemoryTransport;

class InMemoryBrokerTest {

    private final InMemoryBroker broker = new InMemoryBroker();
    private final List<String> received = new ArrayList<>();

    @Test
    void deliversToMatchingSubscriptionsOnly() {
        InMemoryTransport sub = connected();
        sub.subscribe("a/+/c", Qos.AT_MOST_ONCE, (t, p) -> received.add(t));
        InMemoryTransport pub = connected();

        pub.publish("a/b/c", bytes("1"), Qos.AT_MOST_ONCE, false);
        pub.publish("a/b/d", bytes("2"), Qos.AT_MOST_ONCE, false);

        assertThat(received).containsExactly("a/b/c");
    }

    @Test
    void retainedMessageIsDeliveredToLateSubscribers() {
        connected().publish("status", bytes("up"), Qos.AT_LEAST_ONCE, true);

        connected().subscribe("status", Qos.AT_LEAST_ONCE, (t, p) -> received.add(new String(p, StandardCharsets.UTF_8)));

        assertThat(received).containsExactly("up");
    }

    @Test
    void willIsPublishedOnConnectionLossButNotOnCleanClose() {
        connected().subscribe("will", Qos.AT_LEAST_ONCE, (t, p) -> received.add(new String(p, StandardCharsets.UTF_8)));
        WillMessage will = new WillMessage("will", bytes("broken"), Qos.AT_LEAST_ONCE, true);

        InMemoryTransport clean = broker.newClient();
        clean.connect(will);
        clean.close();
        InMemoryTransport crashing = broker.newClient();
        crashing.connect(will);
        crashing.simulateConnectionLoss();

        assertThat(received).containsExactly("broken");
        assertThat(broker.retainedMessage("will")).isEqualTo(bytes("broken"));
    }

    @Test
    void disconnectedClientNoLongerReceives() {
        InMemoryTransport sub = connected();
        sub.subscribe("t", Qos.AT_MOST_ONCE, (t, p) -> received.add(t));
        sub.close();

        connected().publish("t", bytes("x"), Qos.AT_MOST_ONCE, false);

        assertThat(received).isEmpty();
        assertThatThrownBy(() -> sub.publish("t", bytes("x"), Qos.AT_MOST_ONCE, false))
                .isInstanceOf(IllegalStateException.class);
    }

    private InMemoryTransport connected() {
        InMemoryTransport t = broker.newClient();
        t.connect(null);
        return t;
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
