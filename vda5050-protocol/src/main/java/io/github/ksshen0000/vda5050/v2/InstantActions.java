package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;
import java.util.List;

public record InstantActions(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        List<Action> actions) implements Vda5050Message {

    public InstantActions {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    public InstantActions(Header h, List<Action> actions) {
        this(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), actions);
    }

    @Override
    public InstantActions withHeader(Header h) {
        return new InstantActions(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), actions);
    }
}
