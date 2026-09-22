package dsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();
    public static String write(Object o) {
        try { return M.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(String s) {
        try { return s == null ? Map.of() : M.readValue(s, Map.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    @SuppressWarnings("unchecked")
    public static java.util.List<Object> readList(String s) {
        try { return s == null ? java.util.List.of() : M.readValue(s, java.util.List.class); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
