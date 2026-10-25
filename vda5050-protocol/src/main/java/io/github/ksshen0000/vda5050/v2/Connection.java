package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;

/** Published retained with QoS 1; the broker sends CONNECTIONBROKEN as last will. */
public record Connection(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        ConnectionState connectionState) implements Vda5050Message {

    public Connection(Header h, ConnectionState connectionState) {
        this(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), connectionState);
    }

    @Override
    public Connection withHeader(Header h) {
        return new Connection(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), connectionState);
    }
}
