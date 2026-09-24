package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Shared Jackson configuration (stable key order helps diffing fixtures). */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON write failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> readMap(String text) {
        try {
            return MAPPER.readValue(text, java.util.Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid JSON", e);
        }
    }
}
