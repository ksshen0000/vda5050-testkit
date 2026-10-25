package io.github.ksshen0000.vda5050.v2;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * MQTT topic of one vehicle: {@code interfaceName/majorVersion/manufacturer/serialNumber/topic},
 * e.g. {@code uagv/v2/TESTKIT-SIM/AGV-001/state}.
 */
public record TopicPath(String interfaceName, String majorVersion, String manufacturer, String serialNumber,
        Vda5050Topic topic) {

    public static final String DEFAULT_INTERFACE_NAME = "uagv";
    public static final String MAJOR_VERSION = "v2";

    /** Characters allowed in a serial number by the spec. */
    private static final Pattern SERIAL_NUMBER = Pattern.compile("[A-Za-z0-9_.:-]+");

    public TopicPath {
        requireLevel(interfaceName, "interfaceName");
        requireLevel(majorVersion, "majorVersion");
        requireLevel(manufacturer, "manufacturer");
        Objects.requireNonNull(serialNumber, "serialNumber");
        if (!SERIAL_NUMBER.matcher(serialNumber).matches()) {
            throw new IllegalArgumentException("invalid serial number '%s': allowed are A-Z a-z 0-9 _ . : -"
                    .formatted(serialNumber));
        }
        Objects.requireNonNull(topic, "topic");
    }

    public static TopicPath of(String manufacturer, String serialNumber, Vda5050Topic topic) {
        return new TopicPath(DEFAULT_INTERFACE_NAME, MAJOR_VERSION, manufacturer, serialNumber, topic);
    }

    public static TopicPath parse(String topic) {
        String[] levels = topic.split("/", -1);
        if (levels.length != 5) {
            throw new IllegalArgumentException("not a VDA 5050 topic: " + topic);
        }
        return new TopicPath(levels[0], levels[1], levels[2], levels[3], Vda5050Topic.fromSegment(levels[4]));
    }

    /** Subscription filter matching this topic for every vehicle of every manufacturer. */
    public static String filterForAllVehicles(String interfaceName, Vda5050Topic topic) {
        return "%s/%s/+/+/%s".formatted(interfaceName, MAJOR_VERSION, topic.segment());
    }

    public TopicPath withTopic(Vda5050Topic other) {
        return new TopicPath(interfaceName, majorVersion, manufacturer, serialNumber, other);
    }

    @Override
    public String toString() {
        return String.join("/", interfaceName, majorVersion, manufacturer, serialNumber, topic.segment());
    }

    private static void requireLevel(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty() || value.contains("/") || value.contains("+") || value.contains("#")) {
            throw new IllegalArgumentException("invalid topic level %s: '%s'".formatted(name, value));
        }
    }
}
