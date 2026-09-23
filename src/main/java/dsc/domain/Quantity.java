package dsc.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A domain quantity always carries a unit or a coordinate basis.
 * The raw representation from import is preserved; normalization
 * happens only in the derived layer. Values that cannot be converted
 * stay in their original position and get a diagnostic instead of
 * being silently dropped.
 */
public final class Quantity {
    public final double value;
    public final String unit;       // e.g. "degC", "mW", "mg", "K/min"
    public final String basis;      // coordinate basis, e.g. "sample-temperature", "instrument-time"
    public final String raw;        // original textual representation at import

    public Quantity(double value, String unit, String basis, String raw) {
        this.value = value;
        this.unit = unit;
        this.basis = basis;
        this.raw = raw;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("unit", unit);
        m.put("basis", basis);
        m.put("raw", raw);
        return m;
    }
}
