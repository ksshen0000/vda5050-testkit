package io.github.ksshen0000.vda5050.testkit;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.ksshen0000.vda5050.conformance.CheckOptions;
import io.github.ksshen0000.vda5050.conformance.Report;
import io.github.ksshen0000.vda5050.conformance.ReportFormat;
import picocli.CommandLine.Option;

/** Options shared by the commands that produce a report. */
final class CheckSettings {

    enum Format { text, json }

    @Option(names = "--format", description = "Report format: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).",
            defaultValue = "text")
    Format format;

    @Option(names = "--report", paramLabel = "FILE", description = "Write the report to FILE instead of standard output.")
    Path report;

    @Option(names = "--disable", paramLabel = "RULE", split = ",",
            description = "Skip these rules, e.g. --disable RETAIN,TIMESTAMP. See the rules command.")
    List<String> disabled = List.of();

    @Option(names = "--reaction-time", paramLabel = "SECONDS", defaultValue = "5",
            description = "Time a vehicle may take to show an order or action in its state (default: ${DEFAULT-VALUE}).")
    double reactionTime;

    @Option(names = "--state-interval", paramLabel = "SECONDS", defaultValue = "30",
            description = "Longest allowed gap between state messages (default: ${DEFAULT-VALUE}, as in the spec).")
    double stateInterval;

    @Option(names = "--cancel-time", paramLabel = "SECONDS", defaultValue = "60",
            description = "Time a vehicle may take to complete a cancelOrder (default: ${DEFAULT-VALUE}).")
    double cancelTime;

    @Option(names = "--max-clock-skew", paramLabel = "SECONDS", defaultValue = "2",
            description = "Tolerated difference between header timestamp and receive time (default: ${DEFAULT-VALUE}).")
    double maxClockSkew;

    CheckOptions options() {
        Set<String> rules = new HashSet<>();
        disabled.forEach(r -> rules.add(r.strip().toUpperCase()));
        return new CheckOptions(seconds(stateInterval), seconds(reactionTime), seconds(cancelTime), seconds(maxClockSkew), rules);
    }

    /** Prints the report and returns the exit code. */
    int publish(Report report, PrintStream stdout) throws IOException {
        String rendered = format == Format.json ? ReportFormat.json(report) + "\n" : ReportFormat.text(report);
        if (this.report != null) {
            Files.writeString(this.report, rendered, StandardCharsets.UTF_8);
            stdout.println((report.passed() ? "PASS" : "FAIL") + " - report written to " + this.report);
        } else {
            stdout.print(rendered);
        }
        return report.passed() ? 0 : Main.FAILED;
    }

    private static Duration seconds(double s) {
        return Duration.ofMillis(Math.round(s * 1000));
    }
}
