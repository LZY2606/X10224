package dsc.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Json() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Map<String, Object> readMap(String text) {
        try {
            if (text == null || text.isBlank()) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(text, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<Map<String, Object>> readList(String text) {
        try {
            if (text == null || text.isBlank()) {
                return List.of();
            }
            return MAPPER.readValue(text, new TypeReference<List<LinkedHashMap<String, Object>>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
