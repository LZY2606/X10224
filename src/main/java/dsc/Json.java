package dsc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;

/** Minimal JSON helpers shared by store, derive and web layers. */
public final class Json {
    public static final ObjectMapper M = new ObjectMapper();

    private Json() {}

    public static ObjectNode obj() { return M.createObjectNode(); }

    public static ArrayNode arr() { return M.createArrayNode(); }

    public static JsonNode parse(String s) {
        try { return M.readTree(s); }
        catch (Exception e) { throw new IllegalArgumentException("bad json: " + e.getMessage(), e); }
    }

    public static String write(JsonNode n) {
        try { return M.writeValueAsString(n); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static String pretty(JsonNode n) {
        try { return M.writerWithDefaultPrettyPrinter().writeValueAsString(n); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Deterministic deep-merge used by import: overlay wins on scalar conflicts. */
    public static JsonNode merge(JsonNode base, JsonNode overlay) {
        if (base instanceof ObjectNode b && overlay instanceof ObjectNode o) {
            ObjectNode out = b.deepCopy();
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (out.has(e.getKey())) out.set(e.getKey(), merge(out.get(e.getKey()), e.getValue()));
                else out.set(e.getKey(), e.getValue().deepCopy());
            }
            return out;
        }
        return overlay.deepCopy();
    }
}
