package io.github.ksshen0000.vda5050.v2;

/** A node of the current order the vehicle has not passed yet. */
public record NodeState(String nodeId, long sequenceId, String nodeDescription, boolean released,
        NodePosition nodePosition) {
}
