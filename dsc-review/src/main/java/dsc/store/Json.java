package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;

final class Json {
    static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {
    }

    static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    static String write(Object node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new IllegalStateException("json encode failed", e);
        }
    }

    static JsonNode read(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid json payload", e);
        }
    }
}
