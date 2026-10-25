package io.github.ksshen0000.vda5050.testkit;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/** Entry point of the {@code vda5050-testkit} command. */
@Command(name = "vda5050-testkit",
        mixinStandardHelpOptions = true,
        versionProvider = Main.Version.class,
        description = "Checks VDA 5050 traffic for conformance and runs simulated vehicles.",
        subcommands = {CheckCommand.class, RecordCommand.class, SimCommand.class, DemoCommand.class, RulesCommand.class},
        footer = {"", "Exit codes: 0 all rules passed, 1 a rule failed, 2 usage or I/O error."})
public final class Main implements Runnable {

    /** Exit code when at least one rule failed. */
    static final int FAILED = 1;
    /** Exit code for bad arguments, unreadable files or an unreachable broker. */
    static final int ERROR = 2;

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    static CommandLine commandLine() {
        return new CommandLine(new Main())
                .setExecutionExceptionHandler((e, cmd, parsed) -> {
                    cmd.getErr().println("error: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
                    return ERROR;
                })
                .setExitCodeExceptionMapper(e -> ERROR);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    static final class Version implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            String version = Main.class.getPackage().getImplementationVersion();
            return new String[] {"vda5050-testkit " + (version == null ? "dev" : version)};
        }
    }
}
