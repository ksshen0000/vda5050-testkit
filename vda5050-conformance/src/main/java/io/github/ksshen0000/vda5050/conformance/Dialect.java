package io.github.ksshen0000.vda5050.conformance;

import java.util.Set;

/** What differs between major versions of VDA 5050 as far as the checks are concerned. */
public enum Dialect {

    V2("v2", Set.of("order", "instantActions", "state", "visualization", "connection", "factsheet"),
            "CONNECTIONBROKEN", false),
    V3("v3", Set.of("order", "instantActions", "state", "visualization", "connection", "factsheet", "zoneSet",
            "responses"), "CONNECTION_BROKEN", true);

    private final String topicVersion;
    private final Set<String> topics;
    private final String connectionBroken;
    private final boolean separateInstantActionStates;

    Dialect(String topicVersion, Set<String> topics, String connectionBroken, boolean separateInstantActionStates) {
        this.topicVersion = topicVersion;
        this.topics = topics;
        this.connectionBroken = connectionBroken;
        this.separateInstantActionStates = separateInstantActionStates;
    }

    /** The major version topic level, e.g. {@code v2}. */
    public String topicVersion() {
        return topicVersion;
    }

    public Set<String> topics() {
        return topics;
    }

    /** {@code connectionState} the broker publishes as last will. */
    public String connectionBroken() {
        return connectionBroken;
    }

    /** Since 3.0 instant actions report in {@code instantActionStates} instead of {@code actionStates}. */
    public boolean separateInstantActionStates() {
        return separateInstantActionStates;
    }

    /** Whether vehicles must publish the topic with the retain flag. */
    public boolean retainedTopic(String topic, String protocolVersion) {
        if (topic.equals("connection")) {
            return true;
        }
        // 2.0 does not say so for the factsheet; 2.1 and later do
        return topic.equals("factsheet") && !(protocolVersion != null && protocolVersion.startsWith("2.0"));
    }

    /** Topics published by the vehicle rather than by fleet control. */
    public static boolean fromVehicle(String topic) {
        return switch (topic) {
            case "state", "visualization", "connection", "factsheet" -> true;
            default -> false;
        };
    }

    public static Dialect ofTopicVersion(String level) {
        for (Dialect d : values()) {
            if (d.topicVersion.equals(level)) {
                return d;
            }
        }
        return null;
    }

    public static Dialect ofProtocolVersion(String version) {
        if (version == null) {
            return null;
        }
        if (version.startsWith("2.")) {
            return V2;
        }
        if (version.startsWith("3.")) {
            return V3;
        }
        return null;
    }
}
