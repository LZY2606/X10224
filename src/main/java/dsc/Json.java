package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static JsonNode read(String s) {
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
}
