package io.github.ksshen0000.vda5050.conformance;

import static io.github.ksshen0000.vda5050.conformance.TraceBuilder.actionState;
import static io.github.ksshen0000.vda5050.conformance.TraceBuilder.ahead;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Each rule catches the fault it is about, and only that rule complains. */
class RuleFaultsTest {

    /** A vehicle driving A -> B -> C correctly. */
    private static TraceBuilder clean() {
        return new TraceBuilder()
                .at(0).online().idle()
                .at(1).order("o1", 0, 0, 3, "A", "B", "C")
                .at(1.1).state(s -> {
                    s.put("orderId", "o1").put("driving", true);
                    ahead(s, 0, "A", "B", "C");
                })
                .at(6).state(s -> {
                    s.put("orderId", "o1").put("driving", true);
                    ahead(s, 2, "B", "C");
                })
                .at(11).state(s -> {
                    s.put("orderId", "o1");
                    ahead(s, 4, "C");
                });
    }

    private static RuleResult result(Report report, String ruleId) {
        return report.results().stream().filter(r -> r.id().equals(ruleId)).findFirst().orElseThrow();
    }

    /** {@code ruleId} fails with a finding containing {@code text}; all other rules have no errors. */
    private static void failsOnly(Report report, String ruleId, String text) {
        RuleResult r = result(report, ruleId);
        assertThat(r.verdict()).as(ReportFormat.text(report)).isEqualTo(RuleResult.Verdict.FAIL);
        assertThat(r.findings()).anySatisfy(f -> assertThat(f.message()).contains(text));
        assertThat(report.results()).filteredOn(o -> !o.id().equals(ruleId))
                .allSatisfy(o -> assertThat(o.verdict()).as(o.id() + "\n" + ReportFormat.text(report))
                        .isNotEqualTo(RuleResult.Verdict.FAIL));
    }

    @Test
    void cleanTracePasses() {
        Report report = clean().check();
        assertThat(report.findings()).as(ReportFormat.text(report)).isEmpty();
        assertThat(result(report, "ORDER-PROGRESS").verdict()).isEqualTo(RuleResult.Verdict.PASS);
        assertThat(result(report, "ORDER-ACK").verdict()).isEqualTo(RuleResult.Verdict.PASS);
    }

    @Nested
    class MessageFormat {

        @Test
        void serialNumberWithSpace() {
            Report report = clean().raw("uagv/v2/ACME/AGV 2/connection",
                    "{\"headerId\":0,\"timestamp\":\"2026-10-25T08:00:00Z\",\"version\":\"2.1.0\",\"manufacturer\":\"ACME\",\"serialNumber\":\"AGV 2\",\"connectionState\":\"ONLINE\"}")
                    .check();
            failsOnly(report, "TOPIC", "serial number");
        }

        @Test
        void unknownTopic() {
            failsOnly(new TraceBuilder().online().raw(TraceBuilder.PREFIX + "status", "{}").check(), "TOPIC", "not a v2 topic");
        }

        @Test
        void notJson() {
            failsOnly(clean().raw(TraceBuilder.PREFIX + "visualization", "{oops").check(), "PAYLOAD", "not JSON");
        }

        @Test
        void missingRequiredField() {
            Report report = clean().at(12).state(s -> s.remove("batteryState")).check();
            failsOnly(report, "SCHEMA", "batteryState");
        }

        @Test
        void unknownEnumValue() {
            Report report = clean().at(12).state(s -> s.put("operatingMode", "AUTO")).check();
            failsOnly(report, "SCHEMA", "operatingMode");
        }

        @Test
        void serialNumberDiffersFromTopic() {
            Report report = clean().at(12).state(s -> s.put("serialNumber", "AGV-9")).check();
            failsOnly(report, "HEADER-MATCH", "serialNumber");
        }

        @Test
        void headerIdGoesBack() {
            Report report = clean().at(12).state(s -> s.put("headerId", 1)).check();
            failsOnly(report, "HEADER-ID", "headerId 1 follows 3");
        }

        @Test
        void headerIdGapIsOnlyAWarning() {
            Report report = clean().at(12).state(s -> s.put("headerId", 9)).check();
            assertThat(result(report, "HEADER-ID").verdict()).isEqualTo(RuleResult.Verdict.WARN);
            assertThat(report.passed()).isTrue();
        }

        @Test
        void localTimestamp() {
            Report report = clean().at(12).state(s -> s.put("timestamp", "2026-10-25T10:00:12+02:00")).check();
            failsOnly(report, "TIMESTAMP", "not ISO 8601 UTC");
        }

        @Test
        void clockSkewIsAWarning() {
            Report report = clean().at(12).state(s -> s.put("timestamp", "2026-10-25T08:01:00Z")).check();
            assertThat(result(report, "TIMESTAMP").verdict()).isEqualTo(RuleResult.Verdict.WARN);
        }

        @Test
        void connectionNotRetained() {
            TraceBuilder trace = new TraceBuilder();
            byte[] online = ("{\"headerId\":0,\"timestamp\":\"2026-10-25T08:00:00Z\",\"version\":\"2.1.0\","
                    + "\"manufacturer\":\"ACME\",\"serialNumber\":\"AGV-1\",\"connectionState\":\"ONLINE\"}").getBytes(StandardCharsets.UTF_8);
            trace.raw(TraceBuilder.PREFIX + "connection", online, false);
            failsOnly(trace.check(), "RETAIN", "retain flag");
        }
    }

    @Nested
    class Vehicle {

        @Test
        void stateWhileOffline() {
            Report report = clean().at(12).connection("OFFLINE").at(13).idle().check();
            failsOnly(report, "CONNECTION", "OFFLINE");
        }

        @Test
        void neverAnnouncesConnection() {
            Report report = new TraceBuilder().idle().at(5).idle().check();
            failsOnly(report, "CONNECTION", "no connection message");
        }

        @Test
        void stateSilentTooLong() {
            Report report = clean().at(45).state(s -> {
                s.put("orderId", "o1");
                ahead(s, 4, "C");
            }).check();
            failsOnly(report, "STATE-INTERVAL", "no state for 34.0 s");
        }

        @Test
        void orderIgnored() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 2, "A", "B")
                    .at(2).idle().at(8).idle().check();
            failsOnly(report, "ORDER-ACK", "neither shows up");
        }

        @Test
        void rejectionCountsAsAnswer() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 2, "A", "B")
                    .at(1.1).state(s -> s.withArray("errors").addObject().put("errorType", "validationError").put("errorLevel", "WARNING"))
                    .check();
            assertThat(result(report, "ORDER-ACK").verdict()).isEqualTo(RuleResult.Verdict.PASS);
        }

        @Test
        void passedNodeStillListed() {
            Report report = clean().at(12).state(s -> {
                s.put("orderId", "o1");
                ahead(s, 4, "C", "D");
                s.withArray("nodeStates").addObject().put("nodeId", "B").put("sequenceId", 2).put("released", true);
            }).check();
            failsOnly(report, "ORDER-PROGRESS", "still lists B");
        }

        @Test
        void lastNodeGoesBack() {
            Report report = clean().at(12).state(s -> {
                s.put("orderId", "o1");
                ahead(s, 2, "B", "C");
            }).check();
            failsOnly(report, "ORDER-PROGRESS", "went back from 4 to 2");
        }

        @Test
        void skipsANode() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 3, "A", "B", "C")
                    .at(1.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B", "C");
                        s.withArray("nodeStates").remove(0);
                    }).check();
            failsOnly(report, "ORDER-PROGRESS", "skips B");
        }

        @Test
        void finishedActionRestarts() {
            Report report = clean()
                    .at(12).state(s -> {
                        ahead(s.put("orderId", "o1"), 4, "C");
                        actionState(s, "x1", "pick", "FINISHED");
                    })
                    .at(13).state(s -> {
                        ahead(s.put("orderId", "o1"), 4, "C");
                        actionState(s, "x1", "pick", "RUNNING");
                    })
                    .check();
            failsOnly(report, "ACTION-STATES", "went from FINISHED to RUNNING");
        }

        @Test
        void instantActionNeverReported() {
            Report report = clean().at(12).instant("startPause", "p1").at(13).idle().at(20).idle().check();
            failsOnly(report, "ACTION-STATES", "instant action p1");
        }

        @Test
        void cancelKeepsTheRoute() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 3, "A", "B", "C")
                    .at(1.1).state(s -> {
                        s.put("orderId", "o1").put("driving", true);
                        ahead(s, 0, "A", "B", "C");
                    })
                    .at(7).instant("cancelOrder", "c1")
                    .at(8).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 2, "B", "C");
                        actionState(s, "c1", "cancelOrder", "FINISHED");
                    }).check();
            failsOnly(report, "CANCEL-ORDER", "still list");
        }

        @Test
        void cancelWithoutOrderMustFail() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).instant("cancelOrder", "c1")
                    .at(1.1).state(s -> actionState(s, "c1", "cancelOrder", "FINISHED")).check();
            failsOnly(report, "CANCEL-ORDER", "no active order");
        }

        @Test
        void cancelWithoutOrderNeedsTheWarning() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).instant("cancelOrder", "c1")
                    .at(1.1).state(s -> actionState(s, "c1", "cancelOrder", "FAILED")).check();
            failsOnly(report, "CANCEL-ORDER", "noOrderToCancel");
        }
    }

    @Nested
    class FleetControl {

        @Test
        void edgeCountWrong() {
            TraceBuilder trace = new TraceBuilder().online().idle();
            var order = trace.header("order").put("orderId", "o1").put("orderUpdateId", 0);
            order.putArray("nodes").addObject().put("nodeId", "A").put("sequenceId", 0).put("released", true).putArray("actions");
            order.withArray("nodes").addObject().put("nodeId", "B").put("sequenceId", 2).put("released", true).putArray("actions");
            order.putArray("edges");
            Report report = trace.at(1).publish("order", order).at(1.1).state(s -> s.withArray("errors").addObject()
                    .put("errorType", "validationError").put("errorLevel", "WARNING")).check();
            failsOnly(report, "ORDER-STRUCTURE", "2 nodes and 0 edges");
        }

        @Test
        void horizonBeforeBase() {
            TraceBuilder trace = new TraceBuilder().online().idle();
            Report report = trace.at(1).order("o1", 0, 0, 1, "A", "B", "C").check();
            // released: A only, then B and C in the horizon -- valid; make C released to break it
            var order = trace.header("order").put("orderId", "o2").put("orderUpdateId", 0);
            order.putArray("nodes");
            order.putArray("edges");
            String[] ids = {"A", "B", "C"};
            boolean[] released = {true, false, true};
            for (int i = 0; i < 3; i++) {
                order.withArray("nodes").addObject().put("nodeId", ids[i]).put("sequenceId", 2 * i).put("released", released[i]).putArray("actions");
                if (i > 0) {
                    order.withArray("edges").addObject().put("edgeId", ids[i - 1] + ids[i]).put("sequenceId", 2 * i - 1)
                            .put("released", released[i]).put("startNodeId", ids[i - 1]).put("endNodeId", ids[i]).putArray("actions");
                }
            }
            assertThat(result(report, "ORDER-STRUCTURE").verdict()).isEqualTo(RuleResult.Verdict.PASS);
            Report broken = new TraceBuilder().online().idle().at(1).publish("order", order).check();
            assertThat(result(broken, "ORDER-STRUCTURE").verdict()).isEqualTo(RuleResult.Verdict.FAIL);
            assertThat(result(broken, "ORDER-STRUCTURE").findings()).anySatisfy(f -> assertThat(f.message()).contains("after the horizon started"));
        }

        @Test
        void updateDoesNotStartAtDecisionPoint() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 2, "A", "B", "C")
                    .at(1.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                    })
                    .at(3).order("o1", 1, 0, 3, "A", "B", "C")
                    .at(3.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                        s.withArray("errors").addObject().put("errorType", "orderUpdateError").put("errorLevel", "WARNING");
                    }).check();
            failsOnly(report, "ORDER-UPDATE", "decision point of order o1/0 is B");
        }

        @Test
        void rejectedUpdateDoesNotCountAsPrevious() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 2, "A", "B", "C")
                    .at(1.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                    })
                    .at(2).order("o1", 1, 0, 3, "A", "B", "C")
                    .at(2.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                        s.withArray("errors").addObject().put("errorType", "orderUpdateError").put("errorLevel", "WARNING")
                                .putArray("errorReferences").addObject().put("referenceKey", "orderUpdateId").put("referenceValue", "1");
                    })
                    .at(3).order("o1", 2, 2, 2, "B", "C")
                    .at(3.1).state(s -> {
                        s.put("orderId", "o1").put("orderUpdateId", 2);
                        ahead(s, 0, "A", "B", "C");
                    }).check();
            RuleResult update = result(report, "ORDER-UPDATE");
            assertThat(update.findings()).as(ReportFormat.text(report)).hasSize(1);
            assertThat(update.findings().getFirst().message()).contains("o1/1");
            assertThat(result(report, "ORDER-PROGRESS").findings()).isEmpty();
        }

        @Test
        void resendWithChangedContents() {
            Report report = new TraceBuilder().online().idle()
                    .at(1).order("o1", 0, 0, 2, "A", "B")
                    .at(1.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                    })
                    .at(2).order("o1", 0, 0, 3, "A", "B", "C")
                    .at(2.1).state(s -> {
                        s.put("orderId", "o1");
                        ahead(s, 0, "A", "B");
                    }).check();
            failsOnly(report, "ORDER-UPDATE", "different contents");
        }
    }
}
