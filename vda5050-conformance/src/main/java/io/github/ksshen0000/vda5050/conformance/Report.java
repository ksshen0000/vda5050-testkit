package io.github.ksshen0000.vda5050.conformance;

import java.time.Instant;
import java.util.List;

/**
 * Result of checking a trace.
 *
 * @param versions protocol versions seen in message headers
 * @param first    receive time of the first message, {@code null} for an empty trace
 */
public record Report(int messages, List<String> vehicles, List<String> versions, Instant first, Instant last,
        List<RuleResult> results) {

    public Report {
        vehicles = List.copyOf(vehicles);
        versions = List.copyOf(versions);
        results = List.copyOf(results);
    }

    /** True if no rule failed. Warnings do not count. */
    public boolean passed() {
        return results.stream().noneMatch(r -> r.verdict() == RuleResult.Verdict.FAIL);
    }

    public List<Finding> findings() {
        return results.stream().flatMap(r -> r.findings().stream()).toList();
    }

    public long count(RuleResult.Verdict verdict) {
        return results.stream().filter(r -> r.verdict() == verdict).count();
    }
}
