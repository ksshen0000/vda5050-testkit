package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;

/**
 * Header fields every VDA 5050 message starts with. {@code headerId} increases by one per message on
 * each topic of each vehicle.
 */
public record Header(long headerId, Instant timestamp, String version, String manufacturer, String serialNumber) {

    /** Placeholder for messages that a client will stamp with the real header when publishing. */
    public static final Header UNSTAMPED = new Header(0, null, null, null, null);
}
