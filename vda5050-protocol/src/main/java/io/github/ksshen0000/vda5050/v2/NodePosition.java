package io.github.ksshen0000.vda5050.v2;

/**
 * @param theta absolute orientation in radians [-pi, pi], or {@code null} if free
 */
public record NodePosition(
        double x,
        double y,
        Double theta,
        Double allowedDeviationXY,
        Double allowedDeviationTheta,
        String mapId,
        String mapDescription) {

    public static NodePosition of(double x, double y, Double theta, String mapId) {
        return new NodePosition(x, y, theta, null, null, mapId, null);
    }
}
