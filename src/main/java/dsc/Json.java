package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static ObjectNode obj() {
        return M.createObjectNode();
    }

    public static ArrayNode arr() {
        return M.createArrayNode();
    }

    public static ObjectNode parse(String text) {
        try {
            return (ObjectNode) M.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException("bad json: " + e.getMessage(), e);
        }
    }

    public static String write(JsonNode node) {
        try {
            return M.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode copy(JsonNode node) {
        return parse(write(node));
    }
}
