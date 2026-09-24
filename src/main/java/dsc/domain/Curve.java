package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw instrument output: an immutable list of samples plus instrument metadata.
 * The endo/exo direction comes ONLY from archived instrument metadata
 * (`heatFlowSignConvention`), never from file names.
 */
public final class Curve {
    public static final class Point {
        public final double timeS;
        public final double tempC;
        public final double heatFlowMw;
        public final String rawLine; // original representation, preserved

        public Point(double timeS, double tempC, double heatFlowMw, String rawLine) {
            this.timeS = timeS;
            this.tempC = tempC;
            this.heatFlowMw = heatFlowMw;
            this.rawLine = rawLine;
        }
    }

    public final String objectId;
    public final Map<String, String> meta;
    public final List<Point> points;

    public Curve(String objectId, Map<String, String> meta, List<Point> points) {
        this.objectId = objectId;
        this.meta = new LinkedHashMap<>(meta);
        this.points = List.copyOf(points);
    }

    /** Direction convention as archived by the instrument, e.g. "endo_up" / "exo_up". */
    public String signConvention() {
        return meta.get("heatFlowSignConvention");
    }

    public boolean isSignalReversed() {
        return "true".equalsIgnoreCase(meta.getOrDefault("signalReversed", "false"));
    }

    /** Parse CSV text: lines beginning with '#' carry `key=value` metadata. */
    public static Curve parse(String objectId, String text) {
        Map<String, String> meta = new LinkedHashMap<>();
        List<Point> points = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.startsWith("#")) {
                String body = t.substring(1).trim();
                int eq = body.indexOf('=');
                if (eq > 0) meta.put(body.substring(0, eq).trim(), body.substring(eq + 1).trim());
                continue;
            }
            if (t.toLowerCase().startsWith("time")) continue; // header row
            String[] cols = t.split("[,;\\t]");
            if (cols.length < 3) {
                diagnostics.add("row kept unparsed: '" + t + "'");
                continue;
            }
            try {
                points.add(new Point(
                        Double.parseDouble(cols[0].trim()),
                        Double.parseDouble(cols[1].trim()),
                        Double.parseDouble(cols[2].trim()), t));
            } catch (NumberFormatException e) {
                diagnostics.add("row kept unparsed: '" + t + "'");
            }
        }
        if (!diagnostics.isEmpty()) meta.put("importDiagnostics", String.join(" | ", diagnostics));
        return new Curve(objectId, meta, points);
    }
}
