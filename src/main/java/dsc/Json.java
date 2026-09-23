package dsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper COMPACT = new ObjectMapper();

    private Json() {}

    public static String write(Object value) {
        try {
            return COMPACT.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String pretty(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String json) {
        try {
            return COMPACT.readValue(json, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return COMPACT.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
