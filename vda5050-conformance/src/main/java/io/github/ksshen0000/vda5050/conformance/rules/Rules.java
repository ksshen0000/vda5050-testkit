package io.github.ksshen0000.vda5050.conformance.rules;

import java.util.List;

import io.github.ksshen0000.vda5050.conformance.CheckOptions;
import io.github.ksshen0000.vda5050.conformance.Rule;
import io.github.ksshen0000.vda5050.conformance.SchemaCatalog;

/** The built-in rules, in report order: message format first, then vehicle and fleet control behaviour. */
public final class Rules {

    private Rules() {
    }

    /** Fresh instances; rules keep state, so a checker needs its own. */
    public static List<Rule> all(CheckOptions options, SchemaCatalog schemas) {
        return List.of(
                new TopicRule(),
                new PayloadRule(),
                new SchemaRule(schemas),
                new HeaderMatchRule(),
                new HeaderIdRule(),
                new TimestampRule(options.maxClockSkew()),
                new RetainRule(),
                new ConnectionRule(),
                new StateIntervalRule(options.stateInterval()),
                new OrderStructureRule(),
                new OrderUpdateRule(),
                new OrderAckRule(options.reactionTime()),
                new OrderProgressRule(),
                new ActionStatesRule(options.reactionTime()),
                new CancelOrderRule(options.cancelTime()));
    }
}
