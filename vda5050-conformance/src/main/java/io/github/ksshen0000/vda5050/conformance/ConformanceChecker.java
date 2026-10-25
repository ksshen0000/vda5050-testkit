package io.github.ksshen0000.vda5050.conformance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import io.github.ksshen0000.vda5050.conformance.rules.Rules;

/**
 * Runs the rules over a trace. Feed messages in receive order with {@link #accept}, then call
 * {@link #finish}. Works the same for a recorded file and for a live subscription.
 */
public final class ConformanceChecker {

    private final List<Rule> rules;
    private final Set<String> vehicles = new TreeSet<>();
    private final Set<String> versions = new TreeSet<>();
    private int messages;
    private Instant first;
    private Instant last;
    private boolean finished;

    public ConformanceChecker(CheckOptions options) {
        this(Rules.all(options, new SchemaCatalog()).stream()
                .filter(r -> !options.disabledRules().contains(r.id()))
                .toList());
    }

    public ConformanceChecker(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static Report check(List<TraceEntry> trace, CheckOptions options) {
        ConformanceChecker checker = new ConformanceChecker(options);
        trace.forEach(checker::accept);
        return checker.finish();
    }

    public synchronized void accept(TraceEntry entry) {
        if (finished) {
            throw new IllegalStateException("checker already finished");
        }
        Message message = new Message(++messages, entry);
        if (first == null) {
            first = entry.receivedAt();
        }
        last = entry.receivedAt();
        if (message.wellFormedTopic()) {
            vehicles.add(message.vehicle());
        }
        if (message.protocolVersion() != null) {
            versions.add(message.protocolVersion());
        }
        for (Rule rule : rules) {
            rule.onMessage(message);
        }
    }

    public synchronized Report finish() {
        if (!finished) {
            finished = true;
            Instant end = last == null ? Instant.EPOCH : last;
            rules.forEach(r -> r.onEnd(end));
        }
        List<RuleResult> results = new ArrayList<>();
        rules.forEach(r -> results.add(r.result()));
        return new Report(messages, List.copyOf(vehicles), List.copyOf(versions), first, last, results);
    }
}
