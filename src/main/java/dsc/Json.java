package dsc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalArgumentException("JSON serialization failed", e);
        }
    }

    public static Map<String, Object> readObject(String text) {
        try {
            return MAPPER.readValue(text, new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        return MAPPER.convertValue(value, Map.class);
    }
}
