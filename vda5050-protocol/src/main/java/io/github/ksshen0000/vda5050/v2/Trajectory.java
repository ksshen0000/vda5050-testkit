package io.github.ksshen0000.vda5050.v2;

import java.util.List;

/** NURBS trajectory of an edge. A cubic Bezier is degree 3 with knot vector [0,0,0,0,1,1,1,1]. */
public record Trajectory(int degree, List<Double> knotVector, List<ControlPoint> controlPoints) {

    public Trajectory {
        knotVector = List.copyOf(knotVector);
        controlPoints = List.copyOf(controlPoints);
    }

    public static Trajectory cubicBezier(ControlPoint start, ControlPoint c1, ControlPoint c2, ControlPoint end) {
        return new Trajectory(3, List.of(0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0), List.of(start, c1, c2, end));
    }
}
