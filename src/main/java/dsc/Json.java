package dsc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private Json() {}
    public static String write(Object o) {
        try { return Json.M.writeValueAsString(o); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static <T> T read(String s, Class<T> t) {
        try { return Json.M.readValue(s, t); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
