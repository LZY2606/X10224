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

    public static JsonNode parse(String text) {
        try {
            return M.readTree(text);
        } catch (Exception e) {
            throw new IllegalStateException("bad json: " + e.getMessage(), e);
        }
    }

    public static String write(JsonNode node) {
        try {
            return M.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
