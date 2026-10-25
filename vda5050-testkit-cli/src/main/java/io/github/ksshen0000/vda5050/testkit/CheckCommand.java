package io.github.ksshen0000.vda5050.testkit;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

import io.github.ksshen0000.vda5050.conformance.ConformanceChecker;
import io.github.ksshen0000.vda5050.conformance.TraceEntry;
import io.github.ksshen0000.vda5050.conformance.TraceFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

@Command(name = "check", mixinStandardHelpOptions = true,
        description = {"Checks recorded traces (JSON Lines, as written by the record command).",
                "Several files are merged by receive time."})
final class CheckCommand implements Callable<Integer> {

    @Parameters(paramLabel = "TRACE", arity = "1..*", description = "Trace files; - reads standard input.")
    List<Path> traces;

    @Mixin
    CheckSettings settings;

    @Override
    public Integer call() throws IOException {
        List<TraceEntry> entries = new ArrayList<>();
        for (Path trace : traces) {
            try (Reader reader = trace.toString().equals("-")
                    ? new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)
                    : Files.newBufferedReader(trace, StandardCharsets.UTF_8)) {
                entries.addAll(TraceFile.read(reader));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(trace + ": " + e.getMessage(), e);
            }
        }
        if (traces.size() > 1) {
            entries.sort(Comparator.comparing(TraceEntry::receivedAt));
        }
        return settings.publish(ConformanceChecker.check(entries, settings.options()), System.out);
    }
}
