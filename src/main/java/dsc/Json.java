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

    public static JsonNode read(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException("bad json: " + e.getMessage(), e);
        }
    }

    public static String write(Object o) {
        try {
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String writeCompact(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T convert(Object o, Class<T> t) {
        return M.convertValue(o, t);
    }
}
