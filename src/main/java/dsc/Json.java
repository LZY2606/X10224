package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

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
            throw new RuntimeException("bad json: " + abbrev(s), e);
        }
    }

    public static String write(Object o) {
        try {
            return M.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ObjectNode from(Map<String, ?> map) {
        return M.valueToTree(map);
    }

    private static String abbrev(String s) {
        return s == null ? "null" : s.substring(0, Math.min(80, s.length()));
    }
}
