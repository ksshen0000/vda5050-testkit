package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;
import java.util.List;

/**
 * An order: alternating nodes and edges. The released prefix is the base, the rest the horizon.
 * Updates of the same order carry the same {@code orderId} and a higher {@code orderUpdateId}.
 */
public record Order(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        String orderId,
        long orderUpdateId,
        String zoneSetId,
        List<Node> nodes,
        List<Edge> edges) implements Vda5050Message {

    public Order {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public Order(Header h, String orderId, long orderUpdateId, List<Node> nodes, List<Edge> edges) {
        this(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), orderId, orderUpdateId,
                null, nodes, edges);
    }

    @Override
    public Order withHeader(Header h) {
        return new Order(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(), orderId, orderUpdateId, zoneSetId, nodes, edges);
    }
}
