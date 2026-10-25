package io.github.ksshen0000.vda5050.v2;

/**
 * @param batteryCharge state of charge in percent
 * @param reach         estimated remaining range in meters
 */
public record BatteryState(double batteryCharge, Double batteryVoltage, Double batteryHealth, boolean charging,
        Double reach) {
}
