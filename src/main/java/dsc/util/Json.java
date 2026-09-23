package dsc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {
    }

    public static ObjectNode obj() {
        return M.createObjectNode();
    }

    public static ArrayNode arr() {
        return M.createArrayNode();
    }

    public static JsonNode parse(String text) {
        try {
            return M.readTree(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String write(JsonNode node) {
        try {
            return M.writeValueAsString(node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String pretty(JsonNode node) {
        try {
            return M.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ObjectNode obj(Object... kv) {
        ObjectNode o = M.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            o.put((String) kv[i], String.valueOf(kv[i + 1]));
        }
        return o;
    }
}
