package io.github.ksshen0000.vda5050.conformance;

import java.util.List;

/**
 * Outcome of one rule over a whole trace.
 *
 * @param checks       how many situations the rule evaluated
 * @param inconclusive situations the trace did not show enough of to decide
 */
public record RuleResult(String id, String title, Rule.Party party, String section, Verdict verdict, int checks,
        List<Finding> findings, List<String> inconclusive) {

    public RuleResult {
        findings = List.copyOf(findings);
        inconclusive = List.copyOf(inconclusive);
    }

    public enum Verdict {
        PASS,
        /** Only warnings. */
        WARN,
        FAIL,
        /** Nothing decidable, but situations the trace cut short. */
        INCONCLUSIVE,
        /** The trace contains nothing the rule applies to. */
        NOT_APPLICABLE
    }
}
