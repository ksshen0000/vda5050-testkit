package io.github.ksshen0000.vda5050.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of an edge, parameterised by travelled distance. Curves are approximated by a polyline
 * with cumulative lengths so that {@link #at(double)} moves at constant speed along the curve.
 */
public final class EdgeShape {

    static final int CURVE_SAMPLES = 64;

    private final double[] xs;
    private final double[] ys;
    private final double[] cumulative;

    private EdgeShape(List<double[]> points) {
        int n = points.size();
        xs = new double[n];
        ys = new double[n];
        cumulative = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = points.get(i)[0];
            ys[i] = points.get(i)[1];
            if (i > 0) {
                cumulative[i] = cumulative[i - 1] + Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
            }
        }
    }

    public static EdgeShape line(double x0, double y0, double x1, double y1) {
        return new EdgeShape(List.of(new double[] {x0, y0}, new double[] {x1, y1}));
    }

    /** Polyline through the given points, as for a degree-1 trajectory. */
    public static EdgeShape polyline(List<double[]> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("a polyline needs at least 2 points");
        }
        return new EdgeShape(points);
    }

    public static EdgeShape cubicBezier(double[] p0, double[] p1, double[] p2, double[] p3) {
        List<double[]> samples = new ArrayList<>(CURVE_SAMPLES + 1);
        for (int i = 0; i <= CURVE_SAMPLES; i++) {
            double t = (double) i / CURVE_SAMPLES;
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            samples.add(new double[] {
                    a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0],
                    a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]});
        }
        return new EdgeShape(samples);
    }

    public double length() {
        return cumulative[cumulative.length - 1];
    }

    /** Pose at distance {@code s} from the start (clamped to the edge); heading is the tangent. */
    public Pose at(double s) {
        double clamped = Math.max(0, Math.min(s, length()));
        int i = segmentIndex(clamped);
        double segLength = cumulative[i + 1] - cumulative[i];
        double f = segLength == 0 ? 0 : (clamped - cumulative[i]) / segLength;
        double x = xs[i] + f * (xs[i + 1] - xs[i]);
        double y = ys[i] + f * (ys[i + 1] - ys[i]);
        return new Pose(x, y, segmentHeading(i));
    }

    public double startHeading() {
        return segmentHeading(firstNonZeroSegment());
    }

    public double endHeading() {
        return segmentHeading(lastNonZeroSegment());
    }

    private int segmentIndex(double s) {
        for (int i = 0; i < cumulative.length - 2; i++) {
            if (s < cumulative[i + 1]) {
                return i;
            }
        }
        return cumulative.length - 2;
    }

    private double segmentHeading(int i) {
        return Math.atan2(ys[i + 1] - ys[i], xs[i + 1] - xs[i]);
    }

    private int firstNonZeroSegment() {
        for (int i = 0; i < xs.length - 1; i++) {
            if (cumulative[i + 1] > cumulative[i]) {
                return i;
            }
        }
        return 0;
    }

    private int lastNonZeroSegment() {
        for (int i = xs.length - 2; i >= 0; i--) {
            if (cumulative[i + 1] > cumulative[i]) {
                return i;
            }
        }
        return 0;
    }
}
