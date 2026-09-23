package dsc.engine;

import dsc.model.SamplePoint;
import dsc.units.Quantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses instrument trace files in the lab's archival text format:
 *
 * <pre>
 * # instrument: TA-DSC-Q200
 * # temperature-unit: °C
 * # heatflow-unit: mW
 * # polarity: exo-up | reverse
 * # columns: temperature, heatflow
 * 25.000, 0.12
 * ...
 * </pre>
 *
 * Original representations are retained per point. Values that cannot be
 * converted stay in place as {@link Quantity#unconvertible} with a
 * diagnostic; points are never dropped to make a prettier chart.
 */
public final class TraceParser {

    private TraceParser() {}

    public record ParsedTrace(String temperatureUnit, String heatflowUnit, boolean reversePolarity,
                              Map<String, String> headers, List<SamplePoint> points,
                              List<String> fileDiagnostics) {}

    public static ParsedTrace parse(String content) {
        List<String> fileDiags = new ArrayList<>();
        List<SamplePoint> points = new ArrayList<>();
        var headers = new java.util.LinkedHashMap<String, String>();
        String tUnit = "C";
        String hfUnit = "mW";
        boolean reverse = false;

        String[] lines = content.split("\\r?\\n");
        int index = 0;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    String key = body.substring(0, colon).trim().toLowerCase();
                    String val = body.substring(colon + 1).trim();
                    headers.put(key, val);
                    switch (key) {
                        case "temperature-unit" -> tUnit = val;
                        case "heatflow-unit" -> hfUnit = val;
                        case "polarity" -> reverse = val.equalsIgnoreCase("reverse");
                        default -> { }
                    }
                }
                continue;
            }
            String[] parts = line.split("[,;\\t]");
            String rawT = parts.length > 0 ? parts[0].trim() : "";
            String rawHF = parts.length > 1 ? parts[1].trim() : "";
            Quantity t = parseQuantity(rawT, tUnit, "temperature", index, fileDiags);
            Quantity hf = parseQuantity(rawHF, hfUnit, "heatflow", index, fileDiags);
            points.add(new SamplePoint(index, t, hf,
                    (!t.convertible() || !hf.convertible())
                            ? "temperature '" + rawT + "' (" + tUnit + "), heatflow '"
                            + rawHF + "' (" + hfUnit + ")" : null));
            index++;
        }
        if (reverse) {
            fileDiags.add("instrument header declares reverse heat-flow polarity");
        }
        return new ParsedTrace(tUnit, hfUnit, reverse, headers, points, fileDiags);
    }

    private static Quantity parseQuantity(String raw, String unit, String what, int idx,
                                          List<String> fileDiags) {
        try {
            double v = Double.parseDouble(raw);
            if (!Double.isFinite(v)) {
                throw new NumberFormatException("non-finite");
            }
            if (!dsc.units.Units.convertible(unit)) {
                fileDiags.add("point#" + idx + " " + what + ": unit '" + unit
                        + "' has no conversion; raw value kept at origin");
                return Quantity.unconvertible(raw, unit);
            }
            return Quantity.of(v, unit, raw + " " + unit);
        } catch (RuntimeException ex) {
            fileDiags.add("point#" + idx + " " + what + ": unparseable token '" + raw
                    + "'; kept at origin with diagnostic");
            return Quantity.unconvertible(raw, unit);
        }
    }
}
