package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;

/** High-rate position updates for display only; never used for control decisions. */
public record Visualization(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        AgvPosition agvPosition,
        Velocity velocity) implements Vda5050Message {

    public Visualization(Header h, AgvPosition agvPosition, Velocity velocity) {
        this(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), agvPosition, velocity);
    }

    @Override
    public Visualization withHeader(Header h) {
        return new Visualization(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), agvPosition, velocity);
    }
}
