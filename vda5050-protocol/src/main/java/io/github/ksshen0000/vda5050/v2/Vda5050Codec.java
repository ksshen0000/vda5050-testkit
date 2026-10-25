package io.github.ksshen0000.vda5050.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON encoding of VDA 5050 messages: optional fields that are {@code null} are omitted, unknown
 * fields are ignored (forward compatibility with vendor extensions), timestamps are ISO 8601 UTC.
 */
public final class Vda5050Codec {

    private final JsonMapper mapper = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    public byte[] encode(Vda5050Message message) {
        return mapper.writeValueAsBytes(message);
    }

    /**
     * @throws Vda5050DecodingException if the payload is not valid JSON for the given type
     */
    public <T extends Vda5050Message> T decode(byte[] payload, Class<T> type) {
        try {
            return mapper.readValue(payload, type);
        } catch (JacksonException e) {
            throw new Vda5050DecodingException("cannot decode " + type.getSimpleName() + ": " + e.getOriginalMessage(), e);
        }
    }

    public static final class Vda5050DecodingException extends IllegalArgumentException {

        public Vda5050DecodingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
