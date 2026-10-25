package io.github.ksshen0000.vda5050.conformance;

import java.time.Duration;
import java.util.Set;

/**
 * Tunables of the checks.
 *
 * @param stateInterval  longest allowed silence on the state topic while the vehicle is online; the
 *                       specification says 30 s
 * @param reactionTime   how long a vehicle may take to reflect an order or instant action in its state
 * @param cancelTime     how long a vehicle may take to finish a {@code cancelOrder}
 * @param maxClockSkew   largest tolerated difference between a header timestamp and the receive time
 * @param disabledRules  ids of rules to skip
 */
public record CheckOptions(Duration stateInterval, Duration reactionTime, Duration cancelTime, Duration maxClockSkew,
        Set<String> disabledRules) {

    public CheckOptions {
        disabledRules = disabledRules == null ? Set.of() : Set.copyOf(disabledRules);
    }

    public static CheckOptions defaults() {
        return new CheckOptions(Duration.ofSeconds(30), Duration.ofSeconds(5), Duration.ofSeconds(60),
                Duration.ofSeconds(2), Set.of());
    }

    public CheckOptions withDisabledRules(Set<String> rules) {
        return new CheckOptions(stateInterval, reactionTime, cancelTime, maxClockSkew, rules);
    }

    public CheckOptions withReactionTime(Duration reactionTime) {
        return new CheckOptions(stateInterval, reactionTime, cancelTime, maxClockSkew, disabledRules);
    }

    public CheckOptions withMaxClockSkew(Duration maxClockSkew) {
        return new CheckOptions(stateInterval, reactionTime, cancelTime, maxClockSkew, disabledRules);
    }
}
