package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class J {
    public static final ObjectMapper M = new ObjectMapper();

    public static JsonNode parse(String s) {
        try { return M.readTree(s); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static String write(Object o) {
        try { return M.writeValueAsString(o); } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static ObjectNode obj() { return M.createObjectNode(); }

    private J() {}
}
