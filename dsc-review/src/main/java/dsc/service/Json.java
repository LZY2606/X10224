package dsc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private Json() {}

    public static String write(Object o) {
        try { return MAPPER.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static <T> T read(String s, Class<T> type) {
        try { return MAPPER.readValue(s, type); }
        catch (Exception e) { throw new IllegalArgumentException("bad json for " + type.getSimpleName() + ": " + e.getMessage(), e); }
    }
}
