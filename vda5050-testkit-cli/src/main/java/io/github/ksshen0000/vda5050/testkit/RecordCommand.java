package io.github.ksshen0000.vda5050.testkit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.ksshen0000.vda5050.conformance.ConformanceChecker;
import io.github.ksshen0000.vda5050.conformance.TraceEntry;
import io.github.ksshen0000.vda5050.conformance.TraceFile;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

@Command(name = "record", mixinStandardHelpOptions = true,
        description = {"Records all VDA 5050 traffic on a broker into a trace file; stop with Ctrl+C or --duration.",
                "With --check, also checks it and prints the report at the end."})
final class RecordCommand implements Callable<Integer> {

    @Mixin
    BrokerOptions broker;

    @Option(names = {"-o", "--out"}, paramLabel = "FILE",
            description = "Trace file to write (default: vda5050-<time>.jsonl).")
    Path out;

    @Option(names = {"-d", "--duration"}, paramLabel = "SECONDS", description = "Stop after this many seconds.")
    Double duration;

    @Option(names = "--topic", paramLabel = "FILTER",
            description = "Subscription filter (default: <interface>/#).")
    String topic;

    @Option(names = "--mqtt3", description = "Connect with MQTT 3.1.1; the retain flag of live messages is then unknown.")
    boolean mqtt3;

    @Option(names = "--check", description = "Check the traffic and print a report when recording stops.")
    boolean check;

    @Mixin
    CheckSettings settings;

    @Override
    public Integer call() throws Exception {
        Path file = out != null ? out : Path.of("vda5050-%s.jsonl".formatted(
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())));
        String filter = topic != null ? topic : broker.interfaceName + "/#";
        ConformanceChecker checker = check ? new ConformanceChecker(settings.options()) : null;
        AtomicInteger count = new AtomicInteger();
        CountDownLatch stop = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        // Ctrl+C: stop recording, then give the report time to be printed before the JVM exits
        Thread hook = new Thread(() -> {
            stop.countDown();
            try {
                done.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
                Recorder recorder = new Recorder(broker, mqtt3, Clock.systemUTC())) {
            Object lock = new Object();
            recorder.start(filter, entry -> {
                synchronized (lock) {
                    write(writer, entry);
                    if (checker != null) {
                        checker.accept(entry);
                    }
                }
                count.incrementAndGet();
            });
            System.err.printf("recording %s from %s:%d into %s%s%n", filter, broker.host(), broker.port(), file,
                    duration == null ? " - Ctrl+C to stop" : " for %.0f s".formatted(duration));
            Runtime.getRuntime().addShutdownHook(hook);
            if (duration != null) {
                stop.await(Math.round(duration * 1000), TimeUnit.MILLISECONDS);
            } else {
                stop.await();
            }
            synchronized (lock) {
                writer.flush();
            }
        }
        try {
            System.err.printf("%d messages recorded into %s%n", count.get(), file);
            return checker == null ? 0 : settings.publish(checker.finish(), System.out);
        } finally {
            done.countDown();
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException | IllegalArgumentException e) {
                // already shutting down, or the hook was never registered
            }
        }
    }

    private static void write(BufferedWriter writer, TraceEntry entry) {
        try {
            writer.write(TraceFile.formatLine(entry));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
