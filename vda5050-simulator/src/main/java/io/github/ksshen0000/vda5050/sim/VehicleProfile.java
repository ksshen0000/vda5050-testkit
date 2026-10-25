package io.github.ksshen0000.vda5050.sim;

import java.util.Map;
import java.util.Set;

/**
 * Physical and behavioural parameters of a simulated vehicle.
 *
 * @param maxSpeed              m/s
 * @param acceleration          m/s²
 * @param deceleration          m/s²
 * @param rotationSpeed         rad/s for rotation on the spot
 * @param nodeTolerance         m; default allowed deviation when a node does not specify one
 * @param drainPerMeter         battery percent consumed per meter driven
 * @param drainPerSecond        battery percent consumed per second while switched on
 * @param chargePerSecond       battery percent gained per second while charging
 * @param actionDurations       seconds per action type; unlisted supported types take {@code defaultActionDuration}
 * @param customActionTypes     vendor-specific action types accepted in addition to the standard ones
 */
public record VehicleProfile(
        double maxSpeed,
        double acceleration,
        double deceleration,
        double rotationSpeed,
        double nodeTolerance,
        double drainPerMeter,
        double drainPerSecond,
        double chargePerSecond,
        double defaultActionDuration,
        Map<String, Double> actionDurations,
        Set<String> customActionTypes) {

    public VehicleProfile {
        actionDurations = actionDurations == null ? Map.of() : Map.copyOf(actionDurations);
        customActionTypes = customActionTypes == null ? Set.of() : Set.copyOf(customActionTypes);
    }

    /** A small jacking AGV: 1.5 m/s, 0.5 m/s² acceleration, 2 s lift. */
    public static VehicleProfile defaults() {
        return new VehicleProfile(1.5, 0.5, 0.5, Math.PI / 4, 0.1, 0.005, 0.0005, 0.2, 1.0,
                Map.of("pick", 2.0, "drop", 2.0), Set.of());
    }

    public VehicleProfile withMaxSpeed(double maxSpeed) {
        return new VehicleProfile(maxSpeed, acceleration, deceleration, rotationSpeed, nodeTolerance, drainPerMeter,
                drainPerSecond, chargePerSecond, defaultActionDuration, actionDurations, customActionTypes);
    }

    public double durationOf(String actionType) {
        return actionDurations.getOrDefault(actionType, defaultActionDuration);
    }
}
