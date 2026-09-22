package dsc.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.Map;

/** Central Jackson binding. Canonical writer sorts keys so hashes are stable. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper CANON = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json write failed", e);
        }
    }

    public static String canonical(Object value) {
        try {
            return CANON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("json canonical write failed", e);
        }
    }

    public static Map<String, Object> read(String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("bad json payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }
}
