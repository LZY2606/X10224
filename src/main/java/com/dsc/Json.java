package com.dsc;

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

    public static String write(Object node) {
        try {
            return M.writeValueAsString(node);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode read(String text) {
        try {
            return M.readTree(text);
        } catch (Exception e) {
            throw new RuntimeException("bad json: " + abbrev(text), e);
        }
    }

    private static String abbrev(String s) {
        return s == null ? "null" : s.substring(0, Math.min(80, s.length()));
    }
}
