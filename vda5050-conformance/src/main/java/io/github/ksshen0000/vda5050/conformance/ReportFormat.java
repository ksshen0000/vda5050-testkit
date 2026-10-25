package io.github.ksshen0000.vda5050.conformance;

import java.time.Duration;
import java.util.stream.Collectors;

import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Renders a {@link Report} for people (plain text) or for tools (JSON). */
public final class ReportFormat {

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private ReportFormat() {
    }

    public static String text(Report report) {
        StringBuilder out = new StringBuilder("VDA 5050 conformance report\n");
        if (report.first() != null) {
            Duration span = Duration.between(report.first(), report.last());
            out.append("  trace:    %d messages, %s to %s (%s)\n".formatted(report.messages(), report.first(),
                    report.last(), human(span)));
        } else {
            out.append("  trace:    empty\n");
        }
        out.append("  vehicles: ").append(report.vehicles().isEmpty() ? "none" : String.join(", ", report.vehicles())).append('\n');
        out.append("  versions: ").append(report.versions().isEmpty() ? "none" : String.join(", ", report.versions())).append("\n\n");
        int idWidth = report.results().stream().mapToInt(r -> r.id().length()).max().orElse(10);
        int titleWidth = report.results().stream().mapToInt(r -> r.title().length()).max().orElse(10);
        for (RuleResult r : report.results()) {
            out.append(("  %-5s %-" + idWidth + "s  %-" + titleWidth + "s  %s\n").formatted(label(r.verdict()), r.id(),
                    r.title(), details(r)));
            for (Finding f : r.findings()) {
                out.append("        %-7s %s  %s%s\n".formatted(f.severity(), f.vehicle(), f.message(), evidence(f)));
            }
            if (r.verdict() == RuleResult.Verdict.INCONCLUSIVE) {
                r.inconclusive().stream().limit(3).forEach(i -> out.append("        ?       ").append(i).append('\n'));
            }
        }
        out.append('\n').append("Result: ").append(report.passed() ? "PASS" : "FAIL").append(" - ")
                .append(report.count(RuleResult.Verdict.FAIL)).append(" failed, ")
                .append(report.count(RuleResult.Verdict.WARN)).append(" with warnings, ")
                .append(report.count(RuleResult.Verdict.PASS)).append(" passed, ")
                .append(report.count(RuleResult.Verdict.INCONCLUSIVE)).append(" inconclusive, ")
                .append(report.count(RuleResult.Verdict.NOT_APPLICABLE)).append(" not applicable\n");
        out.append("Evidence numbers (#n) are message positions in the trace, counting from 1.\n");
        return out.toString();
    }

    public static String json(Report report) {
        ObjectNode root = JSON.createObjectNode();
        root.put("passed", report.passed());
        root.put("messages", report.messages());
        root.put("first", report.first() == null ? null : report.first().toString());
        root.put("last", report.last() == null ? null : report.last().toString());
        report.vehicles().forEach(root.putArray("vehicles")::add);
        report.versions().forEach(root.putArray("versions")::add);
        ArrayNode rules = root.putArray("rules");
        for (RuleResult r : report.results()) {
            ObjectNode rule = rules.addObject();
            rule.put("id", r.id());
            rule.put("title", r.title());
            rule.put("party", r.party().name());
            rule.put("section", r.section());
            rule.put("verdict", r.verdict().name());
            rule.put("checks", r.checks());
            ArrayNode findings = rule.putArray("findings");
            for (Finding f : r.findings()) {
                ObjectNode finding = findings.addObject();
                finding.put("severity", f.severity().name());
                finding.put("vehicle", f.vehicle());
                finding.put("message", f.message());
                f.evidence().forEach(finding.putArray("evidence")::add);
            }
            r.inconclusive().forEach(rule.putArray("inconclusive")::add);
        }
        return JSON.writeValueAsString(root);
    }

    private static String label(RuleResult.Verdict verdict) {
        return switch (verdict) {
            case PASS -> "PASS";
            case WARN -> "WARN";
            case FAIL -> "FAIL";
            case INCONCLUSIVE -> "?";
            case NOT_APPLICABLE -> "-";
        };
    }

    private static String details(RuleResult r) {
        String party = switch (r.party()) {
            case VEHICLE -> "vehicle";
            case FLEET_CONTROL -> "fleet control";
            case PUBLISHER -> "publisher";
        };
        String checks = r.verdict() == RuleResult.Verdict.NOT_APPLICABLE ? "nothing to check" : r.checks() + " checked";
        return "[%s, %s, spec %s]".formatted(party, checks, r.section());
    }

    private static String evidence(Finding f) {
        if (f.evidence().isEmpty()) {
            return "";
        }
        return f.evidence().stream().map(i -> "#" + i).collect(Collectors.joining(", ", " (", ")"));
    }

    private static String human(Duration d) {
        long s = d.toSeconds();
        if (s < 60) {
            return "%.1f s".formatted(d.toMillis() / 1000.0);
        }
        return s >= 3600 ? "%dh %02dm".formatted(s / 3600, s / 60 % 60) : "%dm %02ds".formatted(s / 60, s % 60);
    }
}
