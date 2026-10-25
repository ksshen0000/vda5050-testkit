package io.github.ksshen0000.vda5050.conformance;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The official VDA 5050 JSON schemas of 2.0.0, 2.1.0 and 3.0.0, with the upstream defects listed in
 * {@code docs/schema-patches.md} corrected.
 */
public final class SchemaCatalog {

    /** Versions with bundled schemas, newest last. */
    public static final List<String> VERSIONS = List.of("2.0.0", "2.1.0", "3.0.0");

    private static final String ROOT = "/io/github/ksshen0000/vda5050/conformance/schemas/";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

    private final Map<String, Optional<Schema>> schemas = new ConcurrentHashMap<>();

    /**
     * Bundled schema version for a header version: same major and minor, any patch. {@code null} if
     * there is none, e.g. for 1.1 or a future 3.1.
     */
    public static String schemaVersionFor(String protocolVersion) {
        if (protocolVersion == null) {
            return null;
        }
        String[] parts = protocolVersion.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        String prefix = parts[0] + "." + parts[1] + ".";
        return VERSIONS.stream().filter(v -> v.startsWith(prefix)).findFirst().orElse(null);
    }

    /** Whether there is a schema for this topic in this version. */
    public boolean has(String schemaVersion, String topic) {
        return schema(schemaVersion, topic).isPresent();
    }

    /** Validation errors; empty if valid or if there is no schema for the topic. */
    public List<String> validate(String schemaVersion, String topic, JsonNode payload) {
        return schema(schemaVersion, topic)
                .map(s -> s.validate(payload).stream().map(SchemaCatalog::describe).toList())
                .orElse(List.of());
    }

    private Optional<Schema> schema(String version, String topic) {
        return schemas.computeIfAbsent(version + "/" + topic, key -> load(version, topic).map(REGISTRY::getSchema));
    }

    private static String describe(Error error) {
        String location = error.getInstanceLocation() == null ? "" : error.getInstanceLocation().toString();
        return location.isEmpty() || location.equals("$") ? error.getMessage() : location + ": " + error.getMessage();
    }

    static Optional<JsonNode> load(String version, String topic) {
        try (InputStream in = SchemaCatalog.class.getResourceAsStream(ROOT + version + "/" + topic + ".schema")) {
            if (in == null) {
                return Optional.empty();
            }
            return Optional.of(patch(version, topic, JSON.readTree(in)));
        } catch (IOException e) {
            throw new IllegalStateException("cannot read schema " + version + "/" + topic, e);
        }
    }

    /** Corrections of upstream defects, see {@code docs/schema-patches.md}. */
    static JsonNode patch(String version, String topic, JsonNode schema) {
        ObjectNode root = (ObjectNode) schema;
        // P1: zoneSet.schema of 3.0.0 names the meta-schema with http instead of https
        if (root.path("$schema").isString() && root.get("$schema").stringValue().startsWith("http://json-schema.org/")) {
            root.put("$schema", root.get("$schema").stringValue().replace("http://", "https://"));
        }
        // P2: 2.x factsheets put the blockingTypes enum on the array instead of its items
        moveArrayEnumToItems(root, "blockingTypes");
        // P3: 2.x state schemas leave PAUSED out of actionStatus although the text (table 2) lists it
        if (version.startsWith("2.") && topic.equals("state")) {
            addEnumValue(root, "actionStatus", "PAUSED");
        }
        return root;
    }

    private static void moveArrayEnumToItems(JsonNode node, String property) {
        if (node.isObject()) {
            JsonNode target = node.path("properties").path(property);
            if (target.isObject() && "array".equals(Json.text(target, "type")) && target.has("enum")
                    && !target.has("items")) {
                ObjectNode t = (ObjectNode) target;
                JsonNode values = t.remove("enum");
                t.putObject("items").put("type", "string").set("enum", values);
            }
        }
        for (JsonNode child : node) {
            moveArrayEnumToItems(child, property);
        }
    }

    private static void addEnumValue(JsonNode node, String property, String value) {
        if (node.isObject()) {
            JsonNode target = node.path("properties").path(property).path("enum");
            if (target.isArray()) {
                boolean present = false;
                for (JsonNode v : target) {
                    present |= value.equals(v.isString() ? v.stringValue() : null);
                }
                if (!present) {
                    ((ArrayNode) target).add(value);
                }
            }
        }
        for (JsonNode child : node) {
            addEnumValue(child, property, value);
        }
    }
}
