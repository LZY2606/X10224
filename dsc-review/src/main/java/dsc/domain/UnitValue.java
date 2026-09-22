package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A measurement that keeps its original textual representation plus a parsed
 * value in the canonical (SI) unit. Values that cannot be converted stay in
 * place with {@code parseError} and a null {@code si} value.
 */
public record UnitValue(String raw, Double si, String unit, String parseError) {

    public boolean valid() {
        return parseError == null && si != null;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("raw", raw);
        m.put("si", si);
        m.put("unit", unit);
        m.put("parseError", parseError);
        return m;
    }
}
