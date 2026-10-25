package io.github.ksshen0000.vda5050.client;

/** Identifies a vehicle on the VDA 5050 interface. */
public record VehicleId(String manufacturer, String serialNumber) {

    @Override
    public String toString() {
        return manufacturer + "/" + serialNumber;
    }
}
