package io.github.ksshen0000.vda5050.v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Every message this library produces must validate against the official VDA 5050 2.1.0 JSON schemas. */
class SchemaComplianceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Vda5050Codec codec = new Vda5050Codec();

    static Stream<Arguments> samples() {
        return Stream.of(
                Arguments.of("order", SampleMessages.order()),
                Arguments.of("instantActions", SampleMessages.instantActions()),
                Arguments.of("state", SampleMessages.state()),
                Arguments.of("visualization", SampleMessages.visualization()),
                Arguments.of("connection", SampleMessages.connection()),
                Arguments.of("factsheet", SampleMessages.factsheet()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void sampleMessageMatchesOfficialSchema(String schemaName, Vda5050Message message) {
        assertThat(Vda5050SchemaValidator.errors(schemaName, codec.encode(message))).isEmpty();
    }

    @Test
    void minimalStateWithOnlyRequiredFieldsIsValid() {
        State minimal = State.of(null, "", 0, "", 0, false, null, null, null, OperatingMode.AUTOMATIC, null, null,
                null, null, null, null, new BatteryState(100, null, null, false, null), null, SafetyState.CLEAR)
                .withHeader(SampleMessages.HEADER);

        assertThat(Vda5050SchemaValidator.errors(Vda5050Topic.STATE, codec.encode(minimal))).isEmpty();
    }

    @Test
    void validatorRejectsAMessageMissingARequiredField() {
        ObjectNode order = (ObjectNode) JSON.readTree(codec.encode(SampleMessages.order()));
        order.remove("nodes");

        assertThat(Vda5050SchemaValidator.errors(Vda5050Topic.ORDER, JSON.writeValueAsBytes(order)))
                .anySatisfy(e -> assertThat(e).contains("nodes"));
    }

    @Test
    void factsheetSchemaDefectIsStillPresentUpstream() {
        // If this fails, the bundled schema no longer has the defect and the patch in the validator can go.
        JsonNode blockingTypes = Vda5050SchemaValidator.loadSchema("factsheet").at(Vda5050SchemaValidator.BLOCKING_TYPES);
        assertThat(blockingTypes.path("type").asString()).isEqualTo("array");
        assertThat(blockingTypes.has("enum")).isTrue();
    }
}
