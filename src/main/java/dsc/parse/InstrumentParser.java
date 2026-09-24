package dsc.parse;

import dsc.model.HeatConvention;
import dsc.model.Point;
import dsc.model.ScanSeries;
import dsc.model.Segment;
import dsc.model.SegmentKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses raw instrument output (CSV with '#' header lines).
 *
 * Semantics guard: the endo/exo direction is taken ONLY from the
 * "convention" header written by the instrument. The file name is never
 * consulted. If the header is absent the convention is UNKNOWN and the
 * caller must keep both sign interpretations as candidates.
 */
public final class InstrumentParser {

    public record Parsed(ScanSeries series, List<String> headerWarnings) {}

    private InstrumentParser() {}

    public static Parsed parse(String seriesId, String raw) {
        List<Segment> segments = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        HeatConvention convention = HeatConvention.UNKNOWN;
        SegmentKind kind = null;
        int cycle = 0;
        List<Point> points = new ArrayList<>();
        boolean sawHeader = false;
        int segIdx = 0;

        String[] lines = raw.split("\r?\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                String body = t.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon < 0) continue;
                String key = body.substring(0, colon).trim().toLowerCase();
                String val = body.substring(colon + 1).trim();
                switch (key) {
                    case "convention" -> {
                        convention = switch (val.toUpperCase()) {
                            case "ENDO_UP" -> HeatConvention.ENDO_UP;
                            case "EXO_UP" -> HeatConvention.EXO_UP;
                            default -> {
                                warnings.add("unknown convention '" + val + "', kept as UNKNOWN");
                                yield HeatConvention.UNKNOWN;
                            }
                        };
                        sawHeader = true;
                    }
                    case "segment" -> {
                        if (kind != null || !points.isEmpty()) {
                            segIdx++;
                            segments.add(new Segment(seriesId + "#" + segIdx,
                                    kind == null ? SegmentKind.HEATING : kind, cycle, convention,
                                    List.copyOf(points)));
                            points = new ArrayList<>();
                        }
                        kind = switch (val.toUpperCase()) {
                            case "HEATING" -> SegmentKind.HEATING;
                            case "COOLING" -> SegmentKind.COOLING;
                            default -> SegmentKind.ISOTHERM;
                        };
                    }
                    case "cycle" -> {
                        try { cycle = Integer.parseInt(val); } catch (NumberFormatException e) {
                            warnings.add("bad cycle header: " + val);
                        }
                    }
                    default -> { /* other headers preserved in raw archive only */ }
                }
                continue;
            }
            if (t.toLowerCase().startsWith("time_s")) continue; // column header
            String[] cols = t.split(",");
            if (cols.length < 3) {
                warnings.add("skipped malformed row: " + t);
                continue;
            }
            try {
                points.add(new Point(
                        Double.parseDouble(cols[0].trim()),
                        Double.parseDouble(cols[1].trim()),
                        Double.parseDouble(cols[2].trim())));
            } catch (NumberFormatException e) {
                warnings.add("skipped unparseable row: " + t);
            }
        }
        if (kind != null || !points.isEmpty()) {
            segIdx++;
            segments.add(new Segment(seriesId + "#" + segIdx,
                    kind == null ? SegmentKind.HEATING : kind, cycle, convention,
                    List.copyOf(points)));
        }
        if (!sawHeader) {
            warnings.add("no convention header: direction is UNKNOWN, both sign readings retained");
        }
        return new Parsed(new ScanSeries(seriesId, List.copyOf(segments)), List.copyOf(warnings));
    }
}
