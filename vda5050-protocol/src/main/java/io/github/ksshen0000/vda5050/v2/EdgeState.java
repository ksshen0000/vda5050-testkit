package io.github.ksshen0000.vda5050.v2;

/** An edge of the current order the vehicle has not finished yet. */
public record EdgeState(String edgeId, long sequenceId, String edgeDescription, boolean released,
        Trajectory trajectory) {
}
