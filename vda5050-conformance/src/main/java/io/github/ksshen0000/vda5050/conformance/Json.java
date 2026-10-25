package io.github.ksshen0000.vda5050.conformance;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

/** Null-safe accessors for possibly malformed payloads: a field of the wrong type reads as absent. */
public final class Json {

    private Json() {
    }

    public static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }

    public static Long integer(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isIntegralNumber() ? value.longValue() : null;
    }

    public static Boolean bool(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isBoolean() ? value.booleanValue() : null;
    }

    /** Elements of an array field that are objects; empty if the field is missing or not an array. */
    public static List<JsonNode> objects(JsonNode node, String field) {
        List<JsonNode> result = new ArrayList<>();
        if (node == null) {
            return result;
        }
        JsonNode array = node.path(field);
        if (array.isArray()) {
            for (JsonNode element : array) {
                if (element.isObject()) {
                    result.add(element);
                }
            }
        }
        return result;
    }

    public static boolean isArray(JsonNode node, String field) {
        return node != null && node.path(field).isArray();
    }
}
