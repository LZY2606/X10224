package com.materials.dsc.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private Json() {
    }

    public static ObjectNode obj() {
        return MAPPER.createObjectNode();
    }

    public static ArrayNode arr() {
        return MAPPER.createArrayNode();
    }

    public static ArrayNode arr(List<JsonNode> nodes) {
        ArrayNode a = arr();
        nodes.forEach(a::add);
        return a;
    }

    public static JsonNode read(String s) {
        try {
            return MAPPER.readTree(s == null || s.isBlank() ? "{}" : s);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid json: " + e.getMessage(), e);
        }
    }

    public static JsonNode readStream(InputStream in) {
        try {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid json stream: " + e.getMessage(), e);
        }
    }

    public static String write(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] writeBytes(Object o) {
        try {
            return MAPPER.writeValueAsBytes(o);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String pretty(Object o) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
