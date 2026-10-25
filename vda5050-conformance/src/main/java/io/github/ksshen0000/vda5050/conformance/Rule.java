package io.github.ksshen0000.vda5050.conformance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One check. The checker feeds every message in trace order to {@link #onMessage}, then calls
 * {@link #onEnd} once. A rule is single-use and not thread-safe.
 */
public abstract class Rule {

    /** Who the specification puts in charge of what the rule checks. */
    public enum Party {
        VEHICLE,
        FLEET_CONTROL,
        /** Whoever published the message, e.g. for schema checks. */
        PUBLISHER
    }

    /** Findings of one kind per vehicle reported in full; the rest are summarized. */
    public static final int MAX_PER_KIND = 5;

    private final List<Finding> findings = new ArrayList<>();
    private final Map<String, Suppressed> perKind = new LinkedHashMap<>();
    private final List<String> inconclusive = new ArrayList<>();
    private int checks;

    /** Stable identifier used in reports and to select rules, e.g. {@code ORDER-ACK}. */
    public abstract String id();

    public abstract String title();

    public abstract Party party();

    /** Section of the specification the rule is based on, e.g. {@code 2.1: 6.6.4 / 3.0: 6.1.4}. */
    public abstract String section();

    public abstract void onMessage(Message message);

    /** @param end receive time of the last message in the trace */
    public void onEnd(Instant end) {
    }

    /** Counts one situation the rule evaluated; a rule that never counts is reported as not applicable. */
    protected final void checked() {
        checks++;
    }

    /**
     * Reports a violation.
     *
     * @param kind short key of the kind of problem; after {@value #MAX_PER_KIND} findings of the same kind
     *             for the same vehicle, further ones are only counted
     */
    protected final void error(String vehicle, String kind, String text, Message... evidence) {
        add(Finding.Severity.ERROR, vehicle, kind, text, evidence);
    }

    /** Reports a likely problem; see {@link #error} for {@code kind}. */
    protected final void warning(String vehicle, String kind, String text, Message... evidence) {
        add(Finding.Severity.WARNING, vehicle, kind, text, evidence);
    }

    /** Records a situation the trace does not show enough of to decide, e.g. because it ends too early. */
    protected final void inconclusive(String text) {
        inconclusive.add(text);
    }

    private void add(Finding.Severity severity, String vehicle, String kind, String text, Message... evidence) {
        Suppressed counter = perKind.computeIfAbsent(vehicle + "\u0000" + kind, k -> new Suppressed(severity, vehicle));
        if (++counter.seen > MAX_PER_KIND) {
            if (severity == Finding.Severity.ERROR) {
                counter.severity = severity;
            }
            return;
        }
        List<Integer> refs = Arrays.stream(evidence).filter(m -> m != null).map(Message::index).sorted().distinct().toList();
        findings.add(new Finding(id(), severity, vehicle, text, refs));
    }

    final RuleResult result() {
        List<Finding> findings = new ArrayList<>(this.findings);
        perKind.forEach((key, c) -> {
            if (c.seen > MAX_PER_KIND) {
                String kind = key.substring(key.indexOf('\u0000') + 1);
                findings.add(new Finding(id(), c.severity, c.vehicle,
                        "%d more of the same kind (%s) not listed".formatted(c.seen - MAX_PER_KIND, kind), List.of()));
            }
        });
        RuleResult.Verdict verdict;
        if (findings.stream().anyMatch(f -> f.severity() == Finding.Severity.ERROR)) {
            verdict = RuleResult.Verdict.FAIL;
        } else if (!findings.isEmpty()) {
            verdict = RuleResult.Verdict.WARN;
        } else if (checks > 0) {
            verdict = RuleResult.Verdict.PASS;
        } else if (!inconclusive.isEmpty()) {
            verdict = RuleResult.Verdict.INCONCLUSIVE;
        } else {
            verdict = RuleResult.Verdict.NOT_APPLICABLE;
        }
        return new RuleResult(id(), title(), party(), section(), verdict, checks, findings, inconclusive);
    }

    private static final class Suppressed {

        Finding.Severity severity;
        final String vehicle;
        int seen;

        Suppressed(Finding.Severity severity, String vehicle) {
            this.severity = severity;
            this.vehicle = vehicle;
        }
    }
}
