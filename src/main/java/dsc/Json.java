package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.Map;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();
    static {
        M.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }
    private Json() {}

    public static String write(Object value) {
        try {
            return M.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String pretty(Object value) {
        try {
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(String text) {
        try {
            return M.readValue(text, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode tree(String text) {
        try {
            return M.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
