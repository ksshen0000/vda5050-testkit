package io.github.ksshen0000.vda5050.conformance;

import java.time.Instant;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A trace entry with its topic split into levels and its payload parsed. Parsing is lenient: rules
 * report what is wrong, so a message is built for any input.
 */
public final class Message {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final int index;
    private final TraceEntry entry;
    private final String[] levels;
    private final JsonNode body;
    private final String parseError;

    Message(int index, TraceEntry entry) {
        this.index = index;
        this.entry = entry;
        this.levels = entry.topic().split("/", -1);
        JsonNode parsed = null;
        String error = null;
        if (entry.payload().length > 0) {
            try {
                parsed = JSON.readTree(entry.payload());
            } catch (JacksonException e) {
                error = e.getOriginalMessage();
            }
        }
        this.body = parsed;
        this.parseError = error;
    }

    /** 1-based position in the trace, used as evidence reference. */
    public int index() {
        return index;
    }

    public TraceEntry entry() {
        return entry;
    }

    public Instant receivedAt() {
        return entry.receivedAt();
    }

    /** Whether the topic has the five levels {@code interfaceName/majorVersion/manufacturer/serialNumber/topic}. */
    public boolean wellFormedTopic() {
        return levels.length == 5;
    }

    public String interfaceName() {
        return wellFormedTopic() ? levels[0] : null;
    }

    public String topicVersion() {
        return wellFormedTopic() ? levels[1] : null;
    }

    public String manufacturer() {
        return wellFormedTopic() ? levels[2] : null;
    }

    public String serialNumber() {
        return wellFormedTopic() ? levels[3] : null;
    }

    /** Last topic level, e.g. {@code state}. */
    public String topicName() {
        return levels[levels.length - 1];
    }

    /** {@code manufacturer/serialNumber} from the topic, or the raw topic if it is malformed. */
    public String vehicle() {
        return wellFormedTopic() ? levels[2] + "/" + levels[3] : entry.topic();
    }

    public boolean fromVehicle() {
        return Dialect.fromVehicle(topicName());
    }

    /** Parsed payload, or {@code null} if empty or not JSON. */
    public JsonNode body() {
        return body;
    }

    public boolean hasObjectBody() {
        return body != null && body.isObject();
    }

    public String parseError() {
        return parseError;
    }

    /** An empty payload clears a retained message; it carries no VDA 5050 content. */
    public boolean isEmpty() {
        return entry.payload().length == 0;
    }

    /** {@code version} from the header, or {@code null}. */
    public String protocolVersion() {
        return Json.text(body, "version");
    }

    /** Dialect from the topic level, falling back to the header version. */
    public Dialect dialect() {
        Dialect fromTopic = Dialect.ofTopicVersion(topicVersion());
        if (fromTopic != null) {
            return fromTopic;
        }
        Dialect fromHeader = Dialect.ofProtocolVersion(protocolVersion());
        return fromHeader != null ? fromHeader : Dialect.V2;
    }

    public Long headerId() {
        return Json.integer(body, "headerId");
    }

    /** The last will, published by the broker; its header was fixed when the vehicle connected. */
    public boolean isLastWill() {
        return topicName().equals("connection") && dialect().connectionBroken().equals(Json.text(body, "connectionState"));
    }

    @Override
    public String toString() {
        return "#" + index + " " + entry.topic();
    }
}
