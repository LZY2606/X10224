package dsc.rules;

import dsc.model.Model.Convention;
import dsc.model.Model.Point;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses instrument output. Header lines ("# key: value") carry metadata such as the
 * endo/exo plotting convention. The convention is read ONLY from this metadata —
 * never inferred from the file name. Raw text is preserved by the caller.
 */
public final class InstrumentParser {

    public record Parsed(
            Map<String, String> header,
            List<Point> points,
            Convention convention,
            String tempUnit,
            String heatFlowUnit,
            boolean heatFlowConvertible,
            List<String> diagnostics) {}

    public static Parsed parse(String rawText, String fileName) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Point> points = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        String[] lines = rawText.split("\\R");
        boolean columnsSeen = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                String body = t.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    header.put(body.substring(0, colon).trim().toLowerCase(),
                            body.substring(colon + 1).trim());
                }
                continue;
            }
            if (!columnsSeen && t.toLowerCase().startsWith("time")) {
                columnsSeen = true;
                continue;
            }
            String[] cols = t.split("[,;\\t]");
            if (cols.length < 3) {
                diagnostics.add("unparseable row kept raw: " + t);
                continue;
            }
            try {
                points.add(new Point(
                        Double.parseDouble(cols[0].trim()),
                        Double.parseDouble(cols[1].trim()),
                        Double.parseDouble(cols[2].trim())));
            } catch (NumberFormatException nfe) {
                diagnostics.add("non-numeric row kept raw: " + t);
            }
        }

        // Direction convention: header metadata only. File name is deliberately ignored.
        String convRaw = header.get("signal_convention");
        Convention convention;
        if (convRaw == null) {
            convention = Convention.AMBIGUOUS;
            diagnostics.add("signal_convention missing in instrument header; "
                    + "endo/exo direction cannot be guessed from file name '" + fileName + "'");
        } else if (convRaw.equalsIgnoreCase("endo_up")) {
            convention = Convention.ENDO_UP;
        } else if (convRaw.equalsIgnoreCase("exo_up")) {
            convention = Convention.EXO_UP;
        } else {
            convention = Convention.AMBIGUOUS;
            diagnostics.add("unknown signal_convention '" + convRaw + "'; kept as AMBIGUOUS");
        }

        String tempUnit = header.getOrDefault("temperature_unit", "C");
        String hfUnit = header.getOrDefault("heat_flow_unit", "mW");
        boolean convertible = hfUnit.equalsIgnoreCase("mW") || hfUnit.equalsIgnoreCase("mJ/s");
        if (!convertible) {
            diagnostics.add("heat flow unit '" + hfUnit + "' not convertible to mW without a "
                    + "calibration constant; values stay in raw position with this diagnostic");
        }
        return new Parsed(header, points, convention, tempUnit, hfUnit, convertible, diagnostics);
    }
}
