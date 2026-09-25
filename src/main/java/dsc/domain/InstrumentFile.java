package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw instrument output, parsed but never normalized in place.
 *
 * Semantics enforced here:
 *  - Heat-flow direction (exo up/down) comes ONLY from the explicit
 *    `sign_convention` header. The file name is stored for provenance but
 *    never consulted for inference.
 *  - Original textual representation of every value is preserved; values that
 *    cannot be converted stay in place and get a diagnostic instead of being
 *    dropped.
 */
public final class InstrumentFile {

    public enum SignConvention { ENDO_UP, EXO_UP, UNDETERMINED }

    public static final class Point {
        public final String rawTime;
        public final String rawTemp;
        public final String rawHeat;
        public final Double timeS;
        public final Double tempC;
        public final Double heatMw;
        public final String diagnostic;

        Point(String rawTime, String rawTemp, String rawHeat,
              Double timeS, Double tempC, Double heatMw, String diagnostic) {
            this.rawTime = rawTime;
            this.rawTemp = rawTemp;
            this.rawHeat = rawHeat;
            this.timeS = timeS;
            this.tempC = tempC;
            this.heatMw = heatMw;
            this.diagnostic = diagnostic;
        }

        public boolean usable() {
            return diagnostic == null;
        }
    }

    public String sourceName = "";
    public final Map<String, String> header = new LinkedHashMap<>();
    public final List<Point> points = new ArrayList<>();
    public final List<String> diagnostics = new ArrayList<>();

    public static InstrumentFile parse(String sourceName, String text) {
        InstrumentFile f = new InstrumentFile();
        f.sourceName = sourceName;
        boolean sawColumns = false;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                String body = t.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    f.header.put(body.substring(0, colon).trim().toLowerCase(),
                            body.substring(colon + 1).trim());
                }
                continue;
            }
            if (t.toLowerCase().startsWith("time_s")) {
                sawColumns = true;
                continue;
            }
            String[] cols = t.split("[,;\\t]");
            if (cols.length < 3) {
                f.diagnostics.add("UNPARSEABLE_ROW: " + t);
                continue;
            }
            Double time = parseDouble(cols[0]);
            Double temp = parseDouble(cols[1]);
            Double heat = parseDouble(cols[2]);
            String diag = null;
            if (time == null || temp == null || heat == null) {
                diag = "UNCONVERTIBLE_VALUE_KEPT_IN_PLACE";
                f.diagnostics.add("row kept with diagnostic: " + t);
            }
            f.points.add(new Point(cols[0].trim(), cols[1].trim(), cols[2].trim(),
                    time, temp, heat, diag));
        }
        if (!sawColumns) {
            f.diagnostics.add("MISSING_COLUMN_HEADER");
        }
        if (f.signConvention() == SignConvention.UNDETERMINED) {
            f.diagnostics.add("DIRECTION_UNDETERMINED: sign_convention header absent or unknown; "
                    + "direction is never guessed from the file name");
        }
        return f;
    }

    private static Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Direction of exothermic deflection, from header metadata only. */
    public SignConvention signConvention() {
        String v = header.get("sign_convention");
        if (v == null) return SignConvention.UNDETERMINED;
        return switch (v.trim().toLowerCase()) {
            case "endo_up" -> SignConvention.ENDO_UP;
            case "exo_up" -> SignConvention.EXO_UP;
            default -> SignConvention.UNDETERMINED;
        };
    }

    public Double scanRateCPerMin() {
        String v = header.get("scan_rate_c_per_min");
        return v == null ? null : parseDouble(v);
    }

    public Double sampleMassMg() {
        String v = header.get("sample_mass_mg");
        return v == null ? null : parseDouble(v);
    }

    public String atmosphere() {
        return header.getOrDefault("atmosphere", "unknown");
    }

    public String calibrationId() {
        return header.getOrDefault("temp_calibration_id", "uncalibrated");
    }
}
