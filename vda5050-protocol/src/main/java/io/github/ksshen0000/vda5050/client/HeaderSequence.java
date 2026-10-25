package io.github.ksshen0000.vda5050.client;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.Vda5050Message;
import io.github.ksshen0000.vda5050.v2.Vda5050Topic;

/** Issues headers with a separate, gap-free {@code headerId} counter per vehicle and topic. */
final class HeaderSequence {

    private final Clock clock;
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();

    HeaderSequence(Clock clock) {
        this.clock = clock;
    }

    Header next(String manufacturer, String serialNumber, Vda5050Topic topic) {
        long id = counters.computeIfAbsent(manufacturer + '/' + serialNumber + '/' + topic.segment(), k -> new AtomicLong())
                .getAndIncrement();
        return new Header(id, clock.instant(), Vda5050Message.PROTOCOL_VERSION, manufacturer, serialNumber);
    }
}
