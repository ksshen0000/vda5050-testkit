package io.github.ksshen0000.vda5050.v2;

import java.time.Instant;

/** Common header accessors of all VDA 5050 messages. */
public interface Vda5050Message {

    /** Protocol version written into every message by this implementation. */
    String PROTOCOL_VERSION = "2.1.0";

    long headerId();

    Instant timestamp();

    String version();

    String manufacturer();

    String serialNumber();

    /**
     * Returns a copy carrying the given header. Clients build messages without caring about the
     * header and stamp it right before publishing.
     */
    Vda5050Message withHeader(Header header);

    default Header header() {
        return new Header(headerId(), timestamp(), version(), manufacturer(), serialNumber());
    }
}
