package io.github.ksshen0000.vda5050.v2;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Validates JSON against the official VDA 5050 2.1.0 schemas bundled in the test resources. Shared with
 * other modules' tests through this module's test-jar.
 */
public final class Vda5050SchemaValidator {

    static final String BLOCKING_TYPES =
            "/properties/protocolFeatures/properties/agvActions/items/properties/blockingTypes";

    private static final SchemaRegistry REGISTRY = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Map<String, Schema> SCHEMAS = new ConcurrentHashMap<>();

    private Vda5050SchemaValidator() {
    }

    /** Validation errors of {@code json} against the schema of {@code topic}; empty if valid. */
    public static List<String> errors(Vda5050Topic topic, byte[] json) {
        return errors(topic.segment(), json);
    }

    static List<String> errors(String schemaName, byte[] json) {
        Schema schema = SCHEMAS.computeIfAbsent(schemaName,
                name -> REGISTRY.getSchema(patchKnownDefects(name, loadSchema(name))));
        return schema.validate(JSON.readTree(json)).stream().map(Error::toString).toList();
    }

    static JsonNode loadSchema(String schemaName) {
        String path = "/vda5050/2.1.0/schemas/" + schemaName + ".schema";
        try (InputStream in = Vda5050SchemaValidator.class.getResourceAsStream(path)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The 2.1.0 factsheet schema declares {@code blockingTypes} as an array but puts the string enum
     * on the array itself, so no array can satisfy it. Move the enum to the items, as the text intends.
     */
    private static JsonNode patchKnownDefects(String schemaName, JsonNode schema) {
        if (schemaName.equals("factsheet")) {
            ObjectNode blockingTypes = (ObjectNode) schema.at(BLOCKING_TYPES);
            JsonNode values = blockingTypes.remove("enum");
            blockingTypes.putObject("items").put("type", "string").set("enum", values);
        }
        return schema;
    }
}
