package io.github.ksshen0000.vda5050.v2;

import java.util.List;

/**
 * @param sequenceId even number; position of the node within the order (nodes and edges alternate)
 * @param released   {@code true} for the base (may be driven to), {@code false} for the horizon
 */
public record Node(
        String nodeId,
        long sequenceId,
        String nodeDescription,
        boolean released,
        NodePosition nodePosition,
        List<Action> actions) {

    public Node {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
