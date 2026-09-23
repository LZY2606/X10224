package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static ObjectNode obj() {
        return M.createObjectNode();
    }

    public static JsonNode parse(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad json: " + e.getMessage(), e);
        }
    }

    public static String write(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T read(String s, Class<T> type) {
        try {
            return M.readValue(s, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("bad json: " + e.getMessage(), e);
        }
    }
}
