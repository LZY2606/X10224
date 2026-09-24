package dsc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static String write(Object value) {
        try {
            return M.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> map(String json) {
        try {
            if (json == null || json.isBlank()) return new LinkedHashMap<>();
            return M.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asMap(Object value) {
        return M.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    }
}
