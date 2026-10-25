package io.github.ksshen0000.vda5050.conformance;

import static io.github.ksshen0000.vda5050.conformance.TraceBuilder.ahead;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.ArrayNode;

/** The behaviour rules also apply to 3.0, with its own names for connection states, errors and arrays. */
class Version3Test {

    private static RuleResult result(Report report, String id) {
        return report.results().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    /** A 3.0 vehicle that drives an order and then answers a cancel it cannot carry out. */
    private static TraceBuilder drive() {
        return TraceBuilder.v3()
                .at(0).online().idle()
                .at(1).order("o1", 0, 0, 2, "A", "B")
                .at(1.1).state(s -> {
                    s.put("orderId", "o1").put("driving", true);
                    ahead(s, 0, "A", "B");
                })
                .at(8).state(s -> {
                    s.put("orderId", "o1");
                    ahead(s, 2, "B");
                })
                .at(9).instant("cancelOrder", "c1");
    }

    @Test
    void conformingVehiclePasses() {
        Report report = drive()
                .at(9.1).state(s -> {
                    s.put("orderId", "o1");
                    ahead(s, 2, "B");
                    ((ArrayNode) s.get("instantActionStates")).addObject().put("actionId", "c1")
                            .put("actionType", "cancelOrder").put("actionStatus", "FAILED");
                    ((ArrayNode) s.get("errors")).addObject().put("errorType", "NO_ORDER_TO_CANCEL").put("errorLevel", "WARNING")
                            .putArray("errorReferences").addObject().put("referenceKey", "actionId").put("referenceValue", "c1");
                })
                .at(10).connection("CONNECTION_BROKEN")
                .check();

        assertThat(report.versions()).containsExactly("3.0.0");
        assertThat(report.findings()).as(ReportFormat.text(report)).isEmpty();
        for (String rule : new String[] {"SCHEMA", "ORDER-ACK", "ORDER-PROGRESS", "ACTION-STATES", "CANCEL-ORDER"}) {
            assertThat(result(report, rule).verdict()).as(rule).isEqualTo(RuleResult.Verdict.PASS);
        }
    }

    @Test
    void instantActionInTheWrongArrayIsAWarning() {
        Report report = drive()
                .at(9.1).state(s -> {
                    s.put("orderId", "o1");
                    ahead(s, 2, "B");
                    TraceBuilder.actionState(s, "c1", "cancelOrder", "FAILED");
                    ((ArrayNode) s.get("errors")).addObject().put("errorType", "NO_ORDER_TO_CANCEL").put("errorLevel", "WARNING");
                })
                .check();
        assertThat(result(report, "ACTION-STATES").verdict()).as(ReportFormat.text(report)).isEqualTo(RuleResult.Verdict.WARN);
        assertThat(result(report, "ACTION-STATES").findings().getFirst().message()).contains("instantActionStates");
    }

    @Test
    void version2NameForTheWillIsASchemaError() {
        Report report = drive().at(10).connection("CONNECTIONBROKEN").check();
        assertThat(result(report, "SCHEMA").verdict()).isEqualTo(RuleResult.Verdict.FAIL);
    }

    @Test
    void version2StateLayoutIsASchemaError() {
        Report report = TraceBuilder.v3().online().state(s -> {
            s.remove("powerSupply");
            s.putObject("batteryState").put("batteryCharge", 80.0).put("charging", false);
        }).check();
        assertThat(result(report, "SCHEMA").findings()).anySatisfy(f -> assertThat(f.message()).contains("powerSupply"));
    }
}
