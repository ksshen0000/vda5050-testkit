package io.github.ksshen0000.vda5050.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @TempDir
    Path dir;

    /** Runs the command and returns exit code and standard output. */
    private static Result run(String... args) {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            int code = Main.commandLine().execute(args);
            return new Result(code, out.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(original);
        }
    }

    private record Result(int code, String out) {
    }

    @Test
    void demoPasses() {
        Result r = run("demo");
        assertThat(r.code()).isZero();
        assertThat(r.out()).contains("Result: PASS");
    }

    @Test
    void demoWithMistakesFailsOnFleetControl() {
        Result r = run("demo", "--with-mistakes", "--format", "json");
        assertThat(r.code()).isEqualTo(Main.FAILED);
        assertThat(r.out()).contains("\"passed\" : false").contains("\"id\" : \"ORDER-UPDATE\"").contains("\"verdict\" : \"FAIL\"");
    }

    @Test
    void checkReadsWhatDemoWrote() throws Exception {
        Path trace = dir.resolve("demo.jsonl");
        assertThat(run("demo", "--with-mistakes", "-o", trace.toString()).code()).isEqualTo(Main.FAILED);

        Result failing = run("check", trace.toString());
        assertThat(failing.code()).isEqualTo(Main.FAILED);
        assertThat(failing.out()).contains("FAIL  ORDER-UPDATE");

        Result disabled = run("check", "--disable", "order-update", trace.toString());
        assertThat(disabled.code()).isZero();
        assertThat(disabled.out()).doesNotContain("ORDER-UPDATE");
    }

    @Test
    void reportToFile() throws Exception {
        Path trace = dir.resolve("demo.jsonl");
        run("demo", "-o", trace.toString());
        Path report = dir.resolve("report.json");
        Result r = run("check", "--format", "json", "--report", report.toString(), trace.toString());
        assertThat(r.code()).isZero();
        assertThat(r.out()).startsWith("PASS");
        assertThat(Files.readString(report)).contains("\"passed\" : true");
    }

    @Test
    void brokenTraceFileIsAnError() throws Exception {
        Path trace = dir.resolve("broken.jsonl");
        Files.writeString(trace, "{\"topic\":\"x\"}\n");
        assertThat(run("check", trace.toString()).code()).isEqualTo(Main.ERROR);
    }

    @Test
    void rulesAreListed() {
        Result r = run("rules");
        assertThat(r.code()).isZero();
        assertThat(r.out()).contains("ORDER-ACK").contains("CANCEL-ORDER").contains("2.0.0, 2.1.0, 3.0.0");
    }
}
