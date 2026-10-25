package io.github.ksshen0000.vda5050.v2.factsheet;

import java.time.Instant;
import java.util.Map;

import io.github.ksshen0000.vda5050.v2.Header;
import io.github.ksshen0000.vda5050.v2.Vda5050Message;

/**
 * Vehicle capabilities. {@code agvGeometry}, {@code loadSpecification} and {@code vehicleConfig} are
 * kept as raw JSON objects: they are passed through but not interpreted yet.
 */
public record Factsheet(
        long headerId,
        Instant timestamp,
        String version,
        String manufacturer,
        String serialNumber,
        TypeSpecification typeSpecification,
        PhysicalParameters physicalParameters,
        ProtocolLimits protocolLimits,
        ProtocolFeatures protocolFeatures,
        Map<String, Object> agvGeometry,
        Map<String, Object> loadSpecification,
        Map<String, Object> vehicleConfig) implements Vda5050Message {

    @Override
    public Factsheet withHeader(Header h) {
        return new Factsheet(h.headerId(), h.timestamp(), h.version(), h.manufacturer(), h.serialNumber(),
                typeSpecification, physicalParameters, protocolLimits, protocolFeatures, agvGeometry,
                loadSpecification, vehicleConfig);
    }
}
