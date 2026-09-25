package dsc.core;

import dsc.model.Model.Diagnostic;
import dsc.model.Model.RawPoint;
import dsc.model.Model.RawSeries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses instrument output. The endo/exo convention comes from the
 * HeatFlowConvention header field only — never from the file name.
 */
public final class InstrumentFile {
    private InstrumentFile() {}

    public record Parsed(RawSeries series, List<Diagnostic> diagnostics) {}

    public static Parsed parse(String id, String sourceName, String text) {
        Map<String, String> header = new LinkedHashMap<>();
        List<RawPoint> points = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();
        String tempUnit = "C", hfUnit = "mW", timeUnit = "s";
        boolean sawColumns = false;
        int lineNo = 0;
        for (String rawLine : text.split("\r?\n")) {
            lineNo++;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    String key = body.substring(0, colon).trim();
                    String value = body.substring(colon + 1).trim();
                    header.put(key, value);
                    switch (key) {
                        case "TempUnit" -> tempUnit = value;
                        case "HeatFlowUnit" -> hfUnit = value;
                        case "TimeUnit" -> timeUnit = value;
                        default -> { }
                    }
                }
                continue;
            }
            if (line.toLowerCase().startsWith("time")) { sawColumns = true; continue; }
            String[] cols = line.split("[,;\\t]");
            if (cols.length < 3) {
                diags.add(Units.diag("ROW_UNPARSEABLE",
                        "line " + lineNo + " has " + cols.length + " columns; kept out of points", id));
                continue;
            }
            try {
                double t = Double.parseDouble(cols[0].trim());
                double temp = Double.parseDouble(cols[1].trim());
                double hf = Double.parseDouble(cols[2].trim());
                points.add(new RawPoint(t, temp, hf));
            } catch (NumberFormatException e) {
                diags.add(Units.diag("ROW_UNPARSEABLE",
                        "line " + lineNo + " not numeric; kept out of points", id));
            }
        }
        if (!sawColumns) {
            diags.add(Units.diag("HEADER_MISSING", "no column header row found; assumed Time,Temp,HeatFlow", id));
        }
        String convention = header.get("HeatFlowConvention");
        if (convention == null || convention.isBlank()) {
            diags.add(Units.diag("DIRECTION_UNKNOWN",
                    "HeatFlowConvention header missing; endo/exo direction left undecided (never guessed from file name '"
                            + sourceName + "')", id));
        }
        RawSeries series = new RawSeries(id, sourceName, header, tempUnit, hfUnit, timeUnit, points);
        return new Parsed(series, diags);
    }

    /** Endo/exo sign convention, from header metadata only. Null when undecidable. */
    public static Boolean endoUp(RawSeries series) {
        String c = series.header().get("HeatFlowConvention");
        if (c == null) return null;
        return switch (c.trim().toLowerCase()) {
            case "endo_up", "endoup", "endo-up" -> true;
            case "exo_up", "exoup", "exo-up" -> false;
            default -> null;
        };
    }
}
