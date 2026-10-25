package io.github.ksshen0000.vda5050.v2;

import io.github.ksshen0000.vda5050.transport.Qos;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;

/** The VDA 5050 2.1 topics with their message type and MQTT settings. */
public enum Vda5050Topic {
    ORDER("order", Order.class, Qos.AT_MOST_ONCE, false),
    INSTANT_ACTIONS("instantActions", InstantActions.class, Qos.AT_MOST_ONCE, false),
    STATE("state", State.class, Qos.AT_MOST_ONCE, false),
    VISUALIZATION("visualization", Visualization.class, Qos.AT_MOST_ONCE, false),
    CONNECTION("connection", Connection.class, Qos.AT_LEAST_ONCE, true),
    // 2.1 section 6.15: "All messages on this topic shall be sent with a retained flag."
    FACTSHEET("factsheet", Factsheet.class, Qos.AT_MOST_ONCE, true);

    private final String segment;
    private final Class<? extends Vda5050Message> messageType;
    private final Qos qos;
    private final boolean retained;

    Vda5050Topic(String segment, Class<? extends Vda5050Message> messageType, Qos qos, boolean retained) {
        this.segment = segment;
        this.messageType = messageType;
        this.qos = qos;
        this.retained = retained;
    }

    /** Last path segment, e.g. {@code instantActions}. */
    public String segment() {
        return segment;
    }

    public Class<? extends Vda5050Message> messageType() {
        return messageType;
    }

    public Qos qos() {
        return qos;
    }

    public boolean retained() {
        return retained;
    }

    public static Vda5050Topic fromSegment(String segment) {
        for (Vda5050Topic topic : values()) {
            if (topic.segment.equals(segment)) {
                return topic;
            }
        }
        throw new IllegalArgumentException("unknown VDA 5050 topic: " + segment);
    }
}
