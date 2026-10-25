package io.github.ksshen0000.vda5050.v2;

/**
 * @param loadId identifier of the load (e.g. the carrier barcode), if known
 * @param weight kg
 */
public record Load(String loadId, String loadType, String loadPosition, BoundingBoxReference boundingBoxReference,
        LoadDimensions loadDimensions, Double weight) {
}
