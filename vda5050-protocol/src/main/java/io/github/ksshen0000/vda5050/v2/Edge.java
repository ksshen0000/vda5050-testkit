package io.github.ksshen0000.vda5050.v2;

import java.util.List;

/**
 * @param sequenceId odd number; position of the edge within the order
 * @param maxSpeed   m/s, or {@code null} for no limit beyond the vehicle's own
 * @param length     meters, optional hint for the vehicle
 */
public record Edge(
        String edgeId,
        long sequenceId,
        String edgeDescription,
        boolean released,
        String startNodeId,
        String endNodeId,
        Double maxSpeed,
        Double maxHeight,
        Double minHeight,
        Double orientation,
        String orientationType,
        String direction,
        Boolean rotationAllowed,
        Double maxRotationSpeed,
        Double length,
        Trajectory trajectory,
        Corridor corridor,
        List<Action> actions) {

    public Edge {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /** An edge with only the commonly used fields set. */
    public static Edge of(String edgeId, long sequenceId, boolean released, String startNodeId, String endNodeId,
            Double maxSpeed, Double length, Trajectory trajectory) {
        return new Edge(edgeId, sequenceId, null, released, startNodeId, endNodeId, maxSpeed, null, null, null, null,
                null, null, null, length, trajectory, null, List.of());
    }
}
