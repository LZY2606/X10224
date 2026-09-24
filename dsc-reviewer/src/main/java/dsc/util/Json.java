package dsc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/** Shared Jackson configuration: stable key order is not relied upon, but pretty JSON aids inspection. */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static byte[] writeBytes(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed", e);
        }
    }

    public static <T> T read(String content, Class<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON parse failed: " + e.getMessage(), e);
        }
    }

    public static <T> T read(byte[] content, Class<T> type) {
        try {
            return MAPPER.readValue(content, type);
        } catch (Exception e) {
            throw new IllegalArgumentException("JSON parse failed: " + e.getMessage(), e);
        }
    }
}
