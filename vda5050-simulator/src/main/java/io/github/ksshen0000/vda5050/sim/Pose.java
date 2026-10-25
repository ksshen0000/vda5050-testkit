package io.github.ksshen0000.vda5050.sim;

/** Position (m) and heading (rad, [-pi, pi]) in the map frame. */
public record Pose(double x, double y, double theta) {

    public double distanceTo(double ox, double oy) {
        return Math.hypot(ox - x, oy - y);
    }

    /** Normalizes an angle to [-pi, pi]. */
    public static double normalize(double angle) {
        double a = Math.IEEEremainder(angle, 2 * Math.PI);
        return a == -Math.PI ? Math.PI : a;
    }

    /** Signed smallest rotation from {@code from} to {@code to}. */
    public static double angleDiff(double from, double to) {
        return normalize(to - from);
    }
}
