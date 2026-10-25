package io.github.ksshen0000.vda5050.v2.factsheet;

/** Speeds in m/s, accelerations in m/s², lengths in m. */
public record PhysicalParameters(
        double speedMin,
        double speedMax,
        double accelerationMax,
        double decelerationMax,
        Double heightMin,
        double heightMax,
        double width,
        double length) {
}
