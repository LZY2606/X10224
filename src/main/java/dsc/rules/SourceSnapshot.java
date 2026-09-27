package dsc.rules;

import dsc.model.Q;
import dsc.model.Source;
import dsc.model.SourceKind;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Current accepted sources on one lineage. The six source kinds are read independently;
 * absent or unconvertible sources become diagnostics, never silent defaults that invent
 * direction, mass or calibration.
 */
public record SourceSnapshot(
        Source instrument,
        Source mass,
        Source rate,
        Source atmosphere,
        Source calibration,
        Source tangent,
        List<String> diagnostics
) {
    public Double massMg() {
        Q q = readQ(mass, "sample.mass", Q.MASS_MG);
        return q == null ? null : q.canonical();
    }

    public Double rateCpm() {
        Q q = readQ(rate, "scan.rate", Q.RATE_C_PER_MIN);
        return q == null ? null : q.canonical();
    }

    public Double calibrationOffsetC() {
        if (calibration == null) {
            return 0.0;
        }
        String raw = calibration.rawFields().getOrDefault("calibration.offsetC", "").trim();
        if (raw.isEmpty()) {
            return 0.0;
        }
        Q q = Q.parse(raw, Q.TEMP_C, Q.TEMP_C);
        return q.valid() ? q.canonical() : null;
    }

    public double rateFractionUncertainty() {
        return fraction(rate, "scan.rateRelativeUncertainty", 0.01);
    }

    public double calibrationFractionUncertainty() {
        return fraction(calibration, "calibration.relativeUncertainty", 0.005);
    }

    private static double fraction(Source s, String key, double fallback) {
        if (s == null) {
            return fallback;
        }
        String raw = s.rawFields().getOrDefault(key, "").trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.replace("%", "").trim())
                    / (raw.contains("%") ? 100.0 : 1.0);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static Q readQ(Source source, String key, String canonicalUnit) {
        if (source == null) {
            return null;
        }
        String raw = source.rawFields().get(key);
        if (raw == null) {
            return null;
        }
        int unitAt = raw.lastIndexOf(' ');
        String value = unitAt >= 0 ? raw.substring(0, unitAt) : raw;
        String unit = unitAt >= 0 ? raw.substring(unitAt + 1) : "";
        return Q.parse(value, unit, canonicalUnit);
    }

    public Map<String, String> revisionMap() {
        Map<String, String> m = new LinkedHashMap<>();
        put(m, "instrument", instrument);
        put(m, "mass", mass);
        put(m, "rate", rate);
        put(m, "atmosphere", atmosphere);
        put(m, "calibration", calibration);
        put(m, "tangent", tangent);
        return m;
    }

    private static void put(Map<String, String> m, String key, Source s) {
        m.put(key, s == null ? null : s.sourceId());
    }

    public Source byKind(SourceKind kind) {
        return switch (kind) {
            case INSTRUMENT_OUTPUT -> instrument;
            case SAMPLE_MASS -> mass;
            case SCAN_RATE -> rate;
            case ATMOSPHERE -> atmosphere;
            case TEMPERATURE_CALIBRATION -> calibration;
            case USER_TANGENT -> tangent;
        };
    }
}
