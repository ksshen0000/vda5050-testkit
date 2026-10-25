package io.github.ksshen0000.vda5050.conformance;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Hand-made traces of one vehicle {@code ACME/AGV-1}, 2.1 by default or 3.0 with {@link #v3()}. Messages
 * get valid headers with consecutive headerIds per topic and the current trace time; tests then break
 * exactly one thing.
 */
final class TraceBuilder {

    static final String PREFIX = "uagv/v2/ACME/AGV-1/";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String prefix;
    private final String version;

    TraceBuilder() {
        this(PREFIX, "2.1.0");
    }

    private TraceBuilder(String prefix, String version) {
        this.prefix = prefix;
        this.version = version;
    }

    /** A 3.0 vehicle on the topic layout 3.0 suggests. */
    static TraceBuilder v3() {
        return new TraceBuilder("vda5050/v3/ACME/AGV-1/", "3.0.0");
    }

    private boolean isV3() {
        return version.startsWith("3.");
    }

    private final List<TraceEntry> entries = new ArrayList<>();
    private final Map<String, Long> headerIds = new HashMap<>();
    private Instant now = Instant.parse("2026-10-25T08:00:00Z");

    TraceBuilder at(double seconds) {
        now = Instant.parse("2026-10-25T08:00:00Z").plus(Duration.ofMillis(Math.round(seconds * 1000)));
        return this;
    }

    TraceBuilder online() {
        return publish("connection", header("connection").put("connectionState", "ONLINE"));
    }

    TraceBuilder connection(String state) {
        return publish("connection", header("connection").put("connectionState", state));
    }

    /** A state; {@code edit} adjusts the idle default. */
    TraceBuilder state(Consumer<ObjectNode> edit) {
        ObjectNode s = header("state");
        s.put("orderId", "").put("orderUpdateId", 0).put("lastNodeId", "A").put("lastNodeSequenceId", 0)
                .put("driving", false).put("operatingMode", "AUTOMATIC");
        s.putArray("nodeStates");
        s.putArray("edgeStates");
        s.putArray("actionStates");
        s.putArray("errors");
        if (isV3()) {
            s.putArray("instantActionStates");
            s.putObject("powerSupply").put("stateOfCharge", 80.0).put("charging", false);
            s.putObject("safetyState").put("activeEmergencyStop", "NONE").put("fieldViolation", false);
        } else {
            s.putObject("batteryState").put("batteryCharge", 80.0).put("charging", false);
            s.putObject("safetyState").put("eStop", "NONE").put("fieldViolation", false);
        }
        edit.accept(s);
        return publish("state", s);
    }

    TraceBuilder idle() {
        return state(s -> {
        });
    }

    /** Order over {@code nodeIds} with released flags from {@code releasedNodes}; node i has sequenceId first + 2i. */
    TraceBuilder order(String orderId, long updateId, long firstSequenceId, int releasedNodes, String... nodeIds) {
        ObjectNode o = header("order").put("orderId", orderId).put("orderUpdateId", updateId);
        ArrayNode nodes = o.putArray("nodes");
        ArrayNode edges = o.putArray("edges");
        for (int i = 0; i < nodeIds.length; i++) {
            long seq = firstSequenceId + 2L * i;
            ObjectNode n = nodes.addObject().put("nodeId", nodeIds[i]).put("sequenceId", seq).put("released", i < releasedNodes);
            n.putArray("actions");
            if (i > 0) {
                ObjectNode e = edges.addObject().put("edgeId", nodeIds[i - 1] + "-" + nodeIds[i]).put("sequenceId", seq - 1)
                        .put("released", i < releasedNodes).put("startNodeId", nodeIds[i - 1]).put("endNodeId", nodeIds[i]);
                e.putArray("actions");
            }
        }
        return publish("order", o);
    }

    TraceBuilder instant(String actionType, String actionId) {
        ObjectNode i = header("instantActions");
        // 3.0 only allows NONE for instant actions
        i.putArray("actions").addObject().put("actionType", actionType).put("actionId", actionId)
                .put("blockingType", isV3() ? "NONE" : "HARD");
        return publish("instantActions", i);
    }

    TraceBuilder publish(String topic, ObjectNode body) {
        return raw(prefix + topic, JSON.writeValueAsBytes(body), null);
    }

    TraceBuilder raw(String topic, byte[] payload, Boolean retained) {
        entries.add(new TraceEntry(now, topic, payload, retained));
        return this;
    }

    TraceBuilder raw(String topic, String payload) {
        return raw(topic, payload.getBytes(StandardCharsets.UTF_8), null);
    }

    ObjectNode header(String topic) {
        long id = headerIds.merge(topic, 1L, Long::sum) - 1;
        return JSON.createObjectNode().put("headerId", id).put("timestamp", now.toString()).put("version", version)
                .put("manufacturer", "ACME").put("serialNumber", "AGV-1");
    }

    /** Remaining nodes and edges, as the vehicle reports them. */
    static void ahead(ObjectNode state, long lastSequenceId, String lastNode, String... remaining) {
        state.put("lastNodeId", lastNode).put("lastNodeSequenceId", lastSequenceId);
        ArrayNode nodes = state.putArray("nodeStates");
        ArrayNode edges = state.putArray("edgeStates");
        String previous = lastNode;
        long seq = lastSequenceId;
        for (String node : remaining) {
            edges.addObject().put("edgeId", previous + "-" + node).put("sequenceId", seq + 1).put("released", true);
            nodes.addObject().put("nodeId", node).put("sequenceId", seq + 2).put("released", true);
            previous = node;
            seq += 2;
        }
    }

    static ObjectNode actionState(ObjectNode state, String actionId, String actionType, String status) {
        ((ArrayNode) state.get("actionStates")).addObject().put("actionId", actionId).put("actionType", actionType)
                .put("actionStatus", status);
        return state;
    }

    List<TraceEntry> build() {
        return List.copyOf(entries);
    }

    Report check() {
        return ConformanceChecker.check(build(), CheckOptions.defaults());
    }
}
