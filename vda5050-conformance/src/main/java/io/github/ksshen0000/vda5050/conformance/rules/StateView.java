package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.ksshen0000.vda5050.conformance.Json;
import io.github.ksshen0000.vda5050.conformance.Message;
import tools.jackson.databind.JsonNode;

/** The parts of a state message the behaviour checks need. */
record StateView(Message message, String orderId, Long orderUpdateId, String lastNodeId, Long lastNodeSequenceId,
        List<Progress> nodeStates, List<Progress> edgeStates, Map<String, ActionStatus> actionStates,
        Map<String, ActionStatus> instantActionStates, List<ErrorView> errors) {

    /** A node or edge still ahead of the vehicle. */
    record Progress(String id, Long sequenceId, Boolean released) {
    }

    record ActionStatus(String actionId, String actionType, String status) {

        boolean terminal() {
            return "FINISHED".equals(status) || "FAILED".equals(status);
        }
    }

    /** @param references {@code referenceKey -> referenceValue} */
    record ErrorView(String errorType, String errorLevel, Map<String, String> references) {
    }

    static StateView of(Message m) {
        JsonNode body = m.body();
        return new StateView(m, Json.text(body, "orderId"), Json.integer(body, "orderUpdateId"),
                Json.text(body, "lastNodeId"), Json.integer(body, "lastNodeSequenceId"),
                progress(body, "nodeStates", "nodeId"), progress(body, "edgeStates", "edgeId"),
                actions(body, "actionStates"), actions(body, "instantActionStates"), errors(body));
    }

    private static List<Progress> progress(JsonNode body, String field, String idField) {
        List<Progress> result = new ArrayList<>();
        for (JsonNode n : Json.objects(body, field)) {
            result.add(new Progress(Json.text(n, idField), Json.integer(n, "sequenceId"), Json.bool(n, "released")));
        }
        return result;
    }

    private static Map<String, ActionStatus> actions(JsonNode body, String field) {
        Map<String, ActionStatus> result = new LinkedHashMap<>();
        for (JsonNode a : Json.objects(body, field)) {
            String id = Json.text(a, "actionId");
            if (id != null) {
                result.put(id, new ActionStatus(id, Json.text(a, "actionType"), Json.text(a, "actionStatus")));
            }
        }
        return result;
    }

    private static List<ErrorView> errors(JsonNode body) {
        List<ErrorView> result = new ArrayList<>();
        for (JsonNode e : Json.objects(body, "errors")) {
            Map<String, String> refs = new LinkedHashMap<>();
            for (JsonNode r : Json.objects(e, "errorReferences")) {
                String key = Json.text(r, "referenceKey");
                JsonNode value = r.path("referenceValue");
                if (key != null && !value.isMissingNode() && !value.isNull()) {
                    refs.put(key, value.isString() ? value.stringValue() : value.toString());
                }
            }
            result.add(new ErrorView(Json.text(e, "errorType"), Json.text(e, "errorLevel"), refs));
        }
        return result;
    }

    /** An action's status in either array; 3.0 keeps instant actions apart. */
    ActionStatus action(String actionId) {
        ActionStatus status = actionStates.get(actionId);
        return status != null ? status : instantActionStates.get(actionId);
    }

    /** No nodes or edges left and every action finished or failed. */
    boolean idle() {
        return nodeStates.isEmpty() && edgeStates.isEmpty()
                && actionStates.values().stream().allMatch(ActionStatus::terminal);
    }

    boolean hasError(java.util.Set<String> types) {
        return errors.stream().anyMatch(e -> types.contains(e.errorType()));
    }

    /** Whether any error references the given key with the given value, e.g. orderId. */
    boolean errorReferences(String key, String value) {
        return value != null && errors.stream().anyMatch(e -> value.equals(e.references().get(key)));
    }
}
