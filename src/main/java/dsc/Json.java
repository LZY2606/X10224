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

    public static String write(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode read(String s) {
        try {
            return M.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode readObj(String s) {
        return (ObjectNode) read(s);
    }

    public static String pretty(String s) {
        try {
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(M.readTree(s));
        } catch (Exception e) {
            return s;
        }
    }
}
