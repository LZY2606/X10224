package dsc.app;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

final class Json {
    private Json() {}

    static List<String> readDeps(String jsonArray) {
        List<String> r = new ArrayList<>();
        try {
            JsonNode n = dsc.engine.State.M.readTree(jsonArray);
            if (n != null && n.isArray()) n.forEach(x -> r.add(x.asText()));
        } catch (Exception ignored) { }
        return r;
    }
}
