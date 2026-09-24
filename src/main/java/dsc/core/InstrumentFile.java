package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses raw instrument output. The original text is always retained by the caller;
 *  this parser only derives a normalized view. Values that cannot be converted stay
 *  in place as raw strings and produce diagnostics instead of being dropped. */
public final class InstrumentFile {

    public record Parsed(
            Map<String, String> header,          // raw header key -> raw value, original representation
            List<DscMath.Point> points,
            List<String> diagnostics,
            String endoConvention,               // "exo-up" | "endo-up" | null (unknown)
            String atmosphere,                   // raw string, e.g. "N2 50 mL/min"
            String scanRateRaw,                  // raw string, e.g. "10 K/min"
            Double scanRateKPerMin               // parsed, null when unconvertible
    ) {}

    public static Parsed parse(String rawText) {
        Map<String, String> header = new LinkedHashMap<>();
        List<DscMath.Point> points = new ArrayList<>();
        List<String> diag = new ArrayList<>();
        String[] lines = rawText.split("\\R");
        boolean inData = false;
        int row = 0;
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) {
                String body = s.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) header.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                continue;
            }
            if (s.regionMatches(true, 0, "time", 0, 4)) { inData = true; continue; } // column header row
            String[] cols = s.split("[,;\\t]");
            if (cols.length < 3) { diag.add("row " + row + ": not enough columns, kept raw: \"" + s + "\""); row++; continue; }
            inData = true;
            try {
                double t = Double.parseDouble(cols[0].trim());
                double temp = Double.parseDouble(cols[1].trim());
                double hf = Double.parseDouble(cols[2].trim());
                points.add(new DscMath.Point(t, temp, hf));
            } catch (NumberFormatException e) {
                diag.add("row " + row + ": unconvertible value left in place: \"" + s + "\"");
            }
            row++;
        }
        if (!inData) diag.add("no data section found");
        String conv = header.get("Convention");
        if (conv != null && !conv.equalsIgnoreCase("exo-up") && !conv.equalsIgnoreCase("endo-up")) {
            diag.add("unknown convention \"" + conv + "\": endo/exo direction undecided, both readings kept");
            conv = null;
        }
        String rateRaw = header.get("ScanRate");
        Double rate = null;
        if (rateRaw != null) {
            try {
                rate = Double.parseDouble(rateRaw.replace("K/min", "").replace("°C/min", "").trim());
            } catch (NumberFormatException e) {
                diag.add("scan rate \"" + rateRaw + "\" unconvertible, kept as raw text");
            }
        }
        return new Parsed(header, points, diag,
                conv == null ? null : conv.toLowerCase(),
                header.get("Atmosphere"), rateRaw, rate);
    }
}
