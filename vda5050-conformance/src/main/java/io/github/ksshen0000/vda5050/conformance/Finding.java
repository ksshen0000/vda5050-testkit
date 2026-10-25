package io.github.ksshen0000.vda5050.conformance;

import java.util.List;

/**
 * Something a rule found wrong.
 *
 * @param vehicle  {@code manufacturer/serialNumber}, or the raw topic if it could not be parsed
 * @param evidence trace positions ({@link Message#index()}) of the messages that show the problem
 */
public record Finding(String ruleId, Severity severity, String vehicle, String message, List<Integer> evidence) {

    public Finding {
        evidence = List.copyOf(evidence);
    }

    public enum Severity {
        /** The specification is violated. */
        ERROR,
        /** Allowed, but likely a problem, or not verifiable from what the observer saw. */
        WARNING
    }
}
