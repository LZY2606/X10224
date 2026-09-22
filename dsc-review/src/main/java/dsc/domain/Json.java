package dsc.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

/** Shared JSON mapper and small helpers; raw representations are always preserved. */
public final class Json {
    public static final ObjectMapper M = new ObjectMapper();
    static { M.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS); }

    private Json() {}

    public static ObjectNode obj() { return M.createObjectNode(); }
    public static ArrayNode arr() { return M.createArrayNode(); }

    public static ObjectNode parseObj(String s) {
        try { return (ObjectNode) M.readTree(s == null || s.isBlank() ? "{}" : s); }
        catch (IOException | ClassCastException e) { throw new IllegalArgumentException("bad json: " + e.getMessage(), e); }
    }

    public static JsonNode parse(String s) {
        try { return M.readTree(s == null || s.isBlank() ? "{}" : s); }
        catch (IOException e) { throw new IllegalArgumentException(e); }
    }

    public static String string(Object o) {
        try { return M.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String pretty(Object o) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
