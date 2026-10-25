package io.github.ksshen0000.vda5050.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import io.github.ksshen0000.vda5050.v2.factsheet.Factsheet;

class Vda5050CodecTest {

    private final Vda5050Codec codec = new Vda5050Codec();

    @Test
    void orderSurvivesARoundTrip() {
        Order order = SampleMessages.order();

        assertThat(codec.decode(codec.encode(order), Order.class)).isEqualTo(order);
    }

    @Test
    void stateSurvivesARoundTrip() {
        State state = SampleMessages.state();

        assertThat(codec.decode(codec.encode(state), State.class)).isEqualTo(state);
    }

    @Test
    void factsheetSurvivesARoundTrip() {
        Factsheet factsheet = SampleMessages.factsheet();

        assertThat(codec.decode(codec.encode(factsheet), Factsheet.class)).isEqualTo(factsheet);
    }

    @Test
    void nullOptionalFieldsAreOmittedAndTimestampIsIso() {
        String json = new String(codec.encode(SampleMessages.connection()), StandardCharsets.UTF_8);

        assertThat(json).contains("\"timestamp\":\"2026-10-18T08:00:00.123Z\"");
        assertThat(json).doesNotContain("null");
    }

    @Test
    void unknownFieldsFromVendorExtensionsAreIgnored() {
        String json = """
                {"headerId": 1, "timestamp": "2026-10-18T08:00:00Z", "version": "2.1.0",
                 "manufacturer": "ACME", "serialNumber": "X1", "connectionState": "ONLINE",
                 "acmeBatteryTemperature": 31.5}
                """;

        Connection connection = codec.decode(json.getBytes(StandardCharsets.UTF_8), Connection.class);

        assertThat(connection.connectionState()).isEqualTo(ConnectionState.ONLINE);
    }

    @Test
    void actionParameterValuesKeepTheirJsonType() {
        Order decoded = codec.decode(codec.encode(SampleMessages.order()), Order.class);
        Action pick = decoded.nodes().get(1).actions().getFirst();

        assertThat(pick.parameter("height")).contains(0.05);
        assertThat(pick.parameter("confirm")).contains(true);
        assertThat(pick.parameter("extra")).contains(Map.of("lane", 2));
        assertThat(pick.parameter("missing")).isEmpty();
    }

    @Test
    void malformedPayloadRaisesDecodingException() {
        assertThatThrownBy(() -> codec.decode("{\"headerId\": \"x\"".getBytes(StandardCharsets.UTF_8), Order.class))
                .isInstanceOf(Vda5050Codec.Vda5050DecodingException.class)
                .hasMessageContaining("Order");
    }
}
