package io.github.ksshen0000.vda5050.conformance;

import static io.github.ksshen0000.vda5050.conformance.SimulatedSite.action;
import static io.github.ksshen0000.vda5050.conformance.SimulatedSite.order;
import static io.github.ksshen0000.vda5050.conformance.SimulatedSite.update;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

/**
 * The bundled simulator is the reference vehicle: whatever a well-behaved fleet control does with it
 * must pass every rule, and every behaviour rule must have had something to check.
 */
class SimulatorConformanceTest {

    @Test
    void simulatedVehiclesPassEveryRule() {
        SimulatedSite site = new SimulatedSite()
                .vehicle("AGV-1", 0, 0, "A")
                .vehicle("AGV-2", 0, 10, "P");
        site.run(1);

        // AGV-1: an order with a horizon, extended by an update, with a pick on the way
        site.send("AGV-1", order("o1", 0).node("A", 0, 0).node("B", 10, 0, action("pick", "pick-1"))
                .horizon().node("C", 20, 0).build());
        site.run(3);
        site.send("AGV-1", update("o1", 1, 2).node("B", 10, 0, action("pick", "pick-1")).node("C", 20, 0).build());
        site.run(2);
        site.instant("AGV-1", action("startPause", "pause-1"));
        site.run(2);
        site.instant("AGV-1", action("stopPause", "resume-1"));
        site.run(40);

        // AGV-2: cancelled on the way, then a cancel with nothing to cancel
        site.send("AGV-2", order("o2", 0).node("P", 0, 10).node("Q", 30, 10).build());
        site.run(5);
        site.instant("AGV-2", action("cancelOrder", "cancel-1"));
        site.run(20);
        site.instant("AGV-2", action("cancelOrder", "cancel-2"));
        site.run(5);

        Report report = ConformanceChecker.check(site.trace(), CheckOptions.defaults());
        System.out.println(ReportFormat.text(report));

        assertThat(report.findings()).isEmpty();
        Map<String, RuleResult.Verdict> verdicts = report.results().stream()
                .collect(Collectors.toMap(RuleResult::id, RuleResult::verdict));
        // The in-memory broker does not pass the retain flag on, so RETAIN has nothing to look at
        assertThat(verdicts).containsEntry("RETAIN", RuleResult.Verdict.NOT_APPLICABLE);
        assertThat(verdicts.entrySet()).filteredOn(e -> !e.getKey().equals("RETAIN"))
                .allSatisfy(e -> assertThat(e.getValue()).as(e.getKey()).isEqualTo(RuleResult.Verdict.PASS));
    }

    @Test
    void traceSurvivesAFileRoundTrip() {
        SimulatedSite site = new SimulatedSite().vehicle("AGV-1", 0, 0, "A");
        site.run(1);
        site.send("AGV-1", order("o1", 0).node("A", 0, 0).node("B", 10, 0).build());
        site.run(15);

        var written = new java.io.StringWriter();
        TraceFile.write(site.trace(), written);
        var read = TraceFile.read(new java.io.StringReader(written.toString()));

        assertThat(read).hasSameSizeAs(site.trace());
        Function<Report, String> json = ReportFormat::json;
        assertThat(json.apply(ConformanceChecker.check(read, CheckOptions.defaults())))
                .isEqualTo(json.apply(ConformanceChecker.check(site.trace(), CheckOptions.defaults())));
    }
}
