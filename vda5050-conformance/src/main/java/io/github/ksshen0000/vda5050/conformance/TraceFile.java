package io.github.ksshen0000.vda5050.conformance;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traces as JSON Lines, one message per line:
 *
 * <pre>{"receivedAt":"2026-10-25T08:00:00.123Z","topic":"uagv/v2/ACME/001/state","retained":false,"payload":{...}}</pre>
 *
 * A payload that is valid JSON is embedded as is, so traces stay readable and diffable; anything else is
 * kept verbatim in {@code payloadText}. {@code retained} is left out when unknown. Blank lines and lines
 * starting with {@code #} are ignored.
 */
public final class TraceFile {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TraceFile() {
    }

    public static List<TraceEntry> read(Reader reader) {
        List<TraceEntry> entries = new ArrayList<>();
        BufferedReader lines = reader instanceof BufferedReader b ? b : new BufferedReader(reader);
        int lineNumber = 0;
        try {
            for (String line; (line = lines.readLine()) != null; ) {
                lineNumber++;
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                entries.add(parseLine(trimmed, lineNumber));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    public static TraceEntry parseLine(String line, int lineNumber) {
        JsonNode node;
        try {
            node = JSON.readTree(line);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("line %d is not JSON: %s".formatted(lineNumber, e.getOriginalMessage()));
        }
        JsonNode receivedAt = node.path("receivedAt");
        JsonNode topic = node.path("topic");
        if (!receivedAt.isString() || !topic.isString()) {
            throw new IllegalArgumentException("line %d needs receivedAt and topic".formatted(lineNumber));
        }
        Instant at;
        try {
            at = Instant.parse(receivedAt.stringValue());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("line %d: receivedAt is not an ISO 8601 instant".formatted(lineNumber));
        }
        byte[] payload;
        if (node.has("payload")) {
            payload = JSON.writeValueAsBytes(node.get("payload"));
        } else if (node.path("payloadText").isString()) {
            payload = node.get("payloadText").stringValue().getBytes(StandardCharsets.UTF_8);
        } else {
            payload = new byte[0];
        }
        JsonNode retained = node.path("retained");
        return new TraceEntry(at, topic.stringValue(), payload, retained.isBoolean() ? retained.booleanValue() : null);
    }

    public static String formatLine(TraceEntry entry) {
        ObjectNode line = JSON.createObjectNode();
        line.put("receivedAt", entry.receivedAt().toString());
        line.put("topic", entry.topic());
        if (entry.retained() != null) {
            line.put("retained", entry.retained());
        }
        if (entry.payload().length > 0) {
            JsonNode payload = tryParse(entry.payload());
            if (payload != null) {
                line.set("payload", payload);
            } else {
                line.put("payloadText", new String(entry.payload(), StandardCharsets.UTF_8));
            }
        }
        return JSON.writeValueAsString(line);
    }

    public static void write(List<TraceEntry> entries, Writer writer) {
        try {
            BufferedWriter out = writer instanceof BufferedWriter b ? b : new BufferedWriter(writer);
            for (TraceEntry entry : entries) {
                out.write(formatLine(entry));
                out.newLine();
            }
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode tryParse(byte[] payload) {
        try {
            JsonNode node = JSON.readTree(payload);
            return node == null || node.isMissingNode() ? null : node;
        } catch (JacksonException e) {
            return null;
        }
    }
}
