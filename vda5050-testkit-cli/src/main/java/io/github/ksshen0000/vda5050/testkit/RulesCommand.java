package io.github.ksshen0000.vda5050.testkit;

import io.github.ksshen0000.vda5050.conformance.CheckOptions;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.SchemaCatalog;
import io.github.ksshen0000.vda5050.conformance.rules.Rules;
import picocli.CommandLine.Command;

@Command(name = "rules", mixinStandardHelpOptions = true, description = "Lists the rules with the spec sections they check.")
final class RulesCommand implements Runnable {

    @Override
    public void run() {
        var rules = Rules.all(CheckOptions.defaults(), new SchemaCatalog());
        int id = rules.stream().mapToInt(r -> r.id().length()).max().orElse(10);
        int title = rules.stream().mapToInt(r -> r.title().length()).max().orElse(10);
        for (Rule r : rules) {
            System.out.printf("%-" + id + "s  %-" + title + "s  %-13s  %s%n", r.id(), r.title(),
                    r.party().name().toLowerCase().replace('_', ' '), r.section());
        }
        System.out.println();
        System.out.println("Schemas bundled for VDA 5050 " + String.join(", ", SchemaCatalog.VERSIONS) + ".");
    }
}
