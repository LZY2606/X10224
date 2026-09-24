package dsc.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {
    private Json() {}
    public static final ObjectMapper M = new ObjectMapper()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    public static String write(Object o) {
        try { return M.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static <T> T read(String s, Class<T> type) {
        try { return M.readValue(s, type); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    public static <T> T readOrNull(String s, Class<T> type) {
        if (s == null || s.isBlank()) return null;
        try { return M.readValue(s, type); }
        catch (Exception e) { return null; }
    }
}
