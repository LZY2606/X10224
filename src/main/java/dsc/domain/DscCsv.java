package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the repository's domain DSC segment format. The format is a small
 * instrument-export style text (NOT a generic table): header key/value pairs
 * declare run, sequence, direction, sign convention, units and rate, followed
 * by raw acquisition rows. Every numeric cell is retained verbatim; conversion
 * is deferred to the derivation layer.
 *
 * Header keys:
 *   run, sequence, direction, sign_convention, temp_unit, hf_unit, scan_rate,
 *   atmosphere
 * Data:
 *   index,temperature,heat_flow
 */
public final class DscCsv {
    private DscCsv() {}

    public record Parsed(Map<String, String> headers, List<Sources.DataPoint> points,
                         List<Sources.Gap> gaps, List<String> diagnostics) {}

    public static Parsed parse(String text) {
        Map<String, String> headers = new LinkedHashMap<>();
        List<Sources.DataPoint> points = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        int lineNo = 0;
        for (String rawLine : lines) {
            lineNo++;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    headers.put(body.substring(0, colon).trim().toLowerCase(),
                            body.substring(colon + 1).trim());
                }
                continue;
            }
            String[] cells = line.split(",", -1);
            if (cells.length < 3) {
                if (!line.toLowerCase().startsWith("index")) {
                    diagnostics.add("line " + lineNo + ": malformed row '" + line + "' retained");
                }
                continue;
            }
            if (lineNo <= lines.length && cells[0].trim().equalsIgnoreCase("index")) continue;
            String idxRaw = cells[0].trim();
            String tRaw = cells[1].trim();
            String hfRaw = cells[2].trim();
            Integer idx = null;
            try { idx = Integer.parseInt(idxRaw); } catch (NumberFormatException ignored) { }
            Quant tq = Quant.parse(tRaw, Quant.TEMPERATURE);
            Quant hq = Quant.parse(hfRaw, Quant.HEAT_FLOW);
            if (!tq.valid()) diagnostics.add("line " + lineNo + ": " + tq.diagnostic() + " ('" + tRaw + "')");
            if (!hq.valid()) diagnostics.add("line " + lineNo + ": " + hq.diagnostic() + " ('" + hfRaw + "')");
            points.add(new Sources.DataPoint(idx == null ? points.size() : idx, tRaw, hfRaw,
                    tq.canonical(), hq.canonical()));
        }
        List<Sources.Gap> gaps = detectGaps(points);
        return new Parsed(headers, points, gaps, diagnostics);
    }

    /**
     * Non-uniform temperature spacing: median consecutive delta is the nominal
     * step; any gap more than 5x the median (and >= 2 deg C) is reported rather
     * than silently interpolated. Integrations later use actual per-step widths.
     */
    public static List<Sources.Gap> detectGaps(List<Sources.DataPoint> pts) {
        List<Double> deltas = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) {
            Double a = pts.get(i - 1).tempC();
            Double b = pts.get(i).tempC();
            if (a != null && b != null) deltas.add(Math.abs(b - a));
        }
        List<Sources.Gap> gaps = new ArrayList<>();
        if (deltas.size() < 4) return gaps;
        List<Double> sorted = new ArrayList<>(deltas);
        sorted.sort(Double::compare);
        double median = sorted.get(sorted.size() / 2);
        for (int i = 1; i < pts.size(); i++) {
            Double a = pts.get(i - 1).tempC();
            Double b = pts.get(i).tempC();
            if (a == null || b == null) continue;
            double d = Math.abs(b - a);
            if (median > 0 && d >= 2.0 && d > 5.0 * median) {
                gaps.add(new Sources.Gap(i - 1, d, median, d / median));
            }
        }
        return gaps;
    }
}
