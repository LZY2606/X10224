package dsc.engine;

import dsc.model.Diagnostic;
import dsc.model.Quantity;
import dsc.model.RawPoint;
import dsc.model.RawSequence;
import dsc.model.SignConvention;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses instrument outputs into a {@link RawSequence}. Parsing never deletes
 * data: rows with unconvertible values are retained with null normalized
 * fields plus an ERROR diagnostic, so they stay in place for the UI/export.
 *
 * Supported text format (domain CSV, not a generic table):
 *   # sequence=SEQ1; direction=heating; sign=endo-up; tempUnit=degC; hfUnit=mW
 *   index,time_s,temp,hf
 *   1,0.0,25.012,0.41
 * Sign may be endo-up / endo-down / unknown; it is a declaration, never a guess.
 */
public final class InstrumentParser {

    private InstrumentParser() {}

    public static RawSequence parseCsv(String sourceId, String content) {
        String direction = null;
        SignConvention sign = SignConvention.UNKNOWN;
        String tempUnit = Quantity.CELSIUS;
        String hfUnit = Quantity.MW;
        String sequenceId = sourceId;
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<RawPoint> points = new ArrayList<>();

        String[] lines = content.split("\\r?\\n", -1);
        int idx = 0;
        boolean headerSeen = false;
        for (String rawLine : lines) {
            String line = rawLine.strip();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String meta = line.substring(1);
                for (String part : meta.split(";")) {
                    int eq = part.indexOf('=');
                    if (eq < 0) continue;
                    String key = part.substring(0, eq).strip().toLowerCase(Locale.ROOT);
                    String val = part.substring(eq + 1).strip();
                    switch (key) {
                        case "sequence" -> sequenceId = val;
                        case "direction" -> direction = val;
                        case "sign" -> sign = parseSign(val);
                        case "tempunit" -> tempUnit = normalizeTempUnit(val);
                        case "hfunit" -> hfUnit = normalizeHfUnit(val);
                        default -> { }
                    }
                }
                continue;
            }
            if (!headerSeen && line.toLowerCase(Locale.ROOT).contains("time")) { headerSeen = true; continue; }
            String[] cols = line.split(",", -1);
            idx++;
            if (cols.length < 4) {
                points.add(new RawPoint(idx, safe(cols, 1), safe(cols, 2), safe(cols, 3), null, null));
                diagnostics.add(Diagnostic.error("IMPORT_COLUMN_COUNT",
                        "row " + idx + " has " + cols.length + " columns, expected 4"));
                continue;
            }
            String timeS = cols[1].strip();
            String tempText = cols[2].strip();
            String hfText = cols[3].strip();
            Double t = null;
            Double hf = null;
            try {
                t = Quantity.toCelsius(Double.parseDouble(tempText), tempUnit);
            } catch (RuntimeException e) {
                diagnostics.add(Diagnostic.error("IMPORT_TEMP_UNCONVERTIBLE",
                        "row " + idx + ": temperature '" + tempText + "' (" + tempUnit
                                + ") could not be converted; raw value retained"));
            }
            try {
                hf = Quantity.toMw(Double.parseDouble(hfText), hfUnit);
            } catch (RuntimeException e) {
                diagnostics.add(Diagnostic.error("IMPORT_HF_UNCONVERTIBLE",
                        "row " + idx + ": heat flow '" + hfText + "' (" + hfUnit
                                + ") could not be converted; raw value retained"));
            }
            points.add(new RawPoint(idx, timeS, tempText, hfText, t, hf));
        }
        if (sign == SignConvention.UNKNOWN) {
            diagnostics.add(Diagnostic.warn("SIGN_UNDECLARED",
                    "no endotherm sign convention declared; event direction cannot be resolved "
                            + "(it must not be inferred from the file name)"));
        }
        if (direction == null || direction.isBlank()) {
            diagnostics.add(Diagnostic.warn("DIRECTION_UNDECLARED",
                    "no heating/cooling declaration; direction will be derived from data monotonicity"));
        }
        return new RawSequence(sourceId, sequenceId, direction == null ? "" : direction,
                sign, tempUnit, hfUnit, points, diagnostics);
    }

    private static String safe(String[] cols, int i) {
        return i < cols.length ? cols[i].strip() : "";
    }

    static SignConvention parseSign(String val) {
        return switch (val.strip().toLowerCase(Locale.ROOT).replace(' ', '-').replace('_', '-')) {
            case "endo-up", "end-up", "positive-endo", "exo-down" -> SignConvention.ENDO_UP;
            case "endo-down", "negative-endo", "exo-up" -> SignConvention.ENDO_DOWN;
            default -> SignConvention.UNKNOWN;
        };
    }

    static String normalizeTempUnit(String val) {
        return switch (val.strip().toLowerCase(Locale.ROOT)) {
            case "c", "°c", "degc", "deg-c", "celsius" -> Quantity.CELSIUS;
            case "k", "kelvin" -> Quantity.KELVIN;
            default -> val;
        };
    }

    static String normalizeHfUnit(String val) {
        return switch (val.strip().toLowerCase(Locale.ROOT)) {
            case "mw", "mwatt" -> Quantity.MW;
            case "w", "watt" -> Quantity.W;
            default -> val;
        };
    }
}
