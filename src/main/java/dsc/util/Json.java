package dsc.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Json {
    public static final ObjectMapper M = new ObjectMapper();
    public static ObjectNode obj() { return M.createObjectNode(); }
    private Json() {}
}
