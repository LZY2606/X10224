package dsc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {
    private static final ObjectMapper M = new ObjectMapper()
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private Json() {}

    public static String write(Object o) {
        try { return M.writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String pretty(Object o) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(o); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static <T> T read(String json, Class<T> type) {
        try { return M.readValue(json, type); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static ObjectMapper mapper() { return M; }
}
