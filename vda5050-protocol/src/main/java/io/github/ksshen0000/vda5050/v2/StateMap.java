package io.github.ksshen0000.vda5050.v2;

/** A map stored on the vehicle (state.maps). */
public record StateMap(String mapId, String mapVersion, String mapDescription, MapStatus mapStatus) {
}
