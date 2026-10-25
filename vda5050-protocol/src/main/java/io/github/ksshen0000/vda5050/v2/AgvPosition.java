package io.github.ksshen0000.vda5050.v2;

public record AgvPosition(
        double x,
        double y,
        double theta,
        String mapId,
        String mapDescription,
        boolean positionInitialized,
        Double localizationScore,
        Double deviationRange) {

    public static AgvPosition of(double x, double y, double theta, String mapId) {
        return new AgvPosition(x, y, theta, mapId, null, true, null, null);
    }
}
