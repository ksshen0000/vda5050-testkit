package io.github.ksshen0000.vda5050.sim;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What a vehicle knows about the site: point positions and path geometry. Orders normally carry
 * positions and trajectories themselves; the map fills in what they leave out.
 */
public record SiteMap(String mapId, String version, Map<String, Point> points, Map<String, Path> paths) {

    public SiteMap {
        points = Map.copyOf(points);
        paths = Map.copyOf(paths);
    }

    public static SiteMap empty(String mapId) {
        return new SiteMap(mapId, "0", Map.of(), Map.of());
    }

    public Optional<Point> point(String code) {
        return Optional.ofNullable(points.get(code));
    }

    public boolean isChargingPoint(String code) {
        Point p = points.get(code);
        return p != null && "CHARGING".equals(p.type());
    }

    /**
     * Shape of the map path {@code code} driven from {@code fromPoint}; reversed if the path is drawn
     * the other way round.
     */
    public Optional<EdgeShape> pathShape(String code, String fromPoint) {
        Path path = paths.get(code);
        if (path == null) {
            return Optional.empty();
        }
        Point from = points.get(path.fromPoint());
        Point to = points.get(path.toPoint());
        if (from == null || to == null) {
            return Optional.empty();
        }
        boolean reversed = path.toPoint().equals(fromPoint);
        Point start = reversed ? to : from;
        Point end = reversed ? from : to;
        if (path.controlPoints().size() == 2) {
            double[] c1 = path.controlPoints().get(reversed ? 1 : 0);
            double[] c2 = path.controlPoints().get(reversed ? 0 : 1);
            return Optional.of(EdgeShape.cubicBezier(start.xy(), c1, c2, end.xy()));
        }
        return Optional.of(EdgeShape.line(start.x(), start.y(), end.x(), end.y()));
    }

    /** @param type point type name as in the map service, e.g. {@code CHARGING} */
    public record Point(double x, double y, String type) {

        double[] xy() {
            return new double[] {x, y};
        }
    }

    /** @param controlPoints empty for straight paths, two {x, y} pairs for Bezier paths */
    public record Path(String fromPoint, String toPoint, List<double[]> controlPoints) {

        public Path {
            controlPoints = List.copyOf(controlPoints);
        }
    }
}
