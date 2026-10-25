package io.github.ksshen0000.vda5050.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TopicPathTest {

    @Test
    void formatsAndParsesTheStandardLayout() {
        TopicPath path = TopicPath.of("TESTKIT-SIM", "AGV-001", Vda5050Topic.INSTANT_ACTIONS);

        assertThat(path.toString()).isEqualTo("uagv/v2/TESTKIT-SIM/AGV-001/instantActions");
        assertThat(TopicPath.parse(path.toString())).isEqualTo(path);
    }

    @Test
    void wildcardFilterCoversAllVehicles() {
        assertThat(TopicPath.filterForAllVehicles("uagv", Vda5050Topic.STATE)).isEqualTo("uagv/v2/+/+/state");
    }

    @Test
    void serialNumberCharactersAreRestricted() {
        assertThatThrownBy(() -> TopicPath.of("M", "AGV 1", Vda5050Topic.STATE)).hasMessageContaining("serial number");
        assertThat(TopicPath.of("M", "a_Z.0:-9", Vda5050Topic.STATE).serialNumber()).isEqualTo("a_Z.0:-9");
    }

    @Test
    void wildcardsAreNotAllowedInLevels() {
        assertThatThrownBy(() -> TopicPath.of("M/X", "AGV1", Vda5050Topic.STATE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TopicPath.of("+", "AGV1", Vda5050Topic.STATE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRejectsForeignTopics() {
        assertThatThrownBy(() -> TopicPath.parse("uagv/v2/M/S")).hasMessageContaining("not a VDA 5050 topic");
        assertThatThrownBy(() -> TopicPath.parse("uagv/v2/M/S/telemetry")).hasMessageContaining("unknown");
    }

    @Test
    void connectionTopicIsRetainedWithQos1() {
        assertThat(Vda5050Topic.CONNECTION.retained()).isTrue();
        assertThat(Vda5050Topic.CONNECTION.qos()).isEqualTo(io.github.ksshen0000.vda5050.transport.Qos.AT_LEAST_ONCE);
        assertThat(Vda5050Topic.STATE.retained()).isFalse();
        assertThat(Vda5050Topic.FACTSHEET.retained()).isTrue();
    }
}
