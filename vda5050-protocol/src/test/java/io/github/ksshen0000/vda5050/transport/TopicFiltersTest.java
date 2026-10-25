package io.github.ksshen0000.vda5050.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TopicFiltersTest {

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({
            "uagv/v2/+/+/state, uagv/v2/M/S/state, true",
            "uagv/v2/+/+/state, uagv/v2/M/S/order, false",
            "uagv/v2/+/+/state, uagv/v2/M/state, false",
            "uagv/#, uagv/v2/M/S/state, true",
            "uagv/v2/M/S/state, uagv/v2/M/S/state, true",
            "uagv/v2/M/S/state, uagv/v2/M/S/state/x, false",
            "uagv/+, uagv, false"
    })
    void matchesLikeAnMqttBroker(String filter, String topic, boolean expected) {
        assertThat(TopicFilters.matches(filter, topic)).isEqualTo(expected);
    }
}
