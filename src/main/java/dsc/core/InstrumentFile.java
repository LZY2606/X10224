package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a DSC instrument export. The raw text is always preserved verbatim in
 * the source archive; parsing only feeds the derived layer. Rows whose values
 * cannot be converted are kept as diagnostics, never silently dropped.
 */
public final class InstrumentFile {

    public record ParseIssue(int line, String raw, String reason) {}

    public final Map<String, String> header = new LinkedHashMap<>();
    public final List<DscPoint> points = new ArrayList<>();
    public final List<ParseIssue> issues = new ArrayList<>();

    public static InstrumentFile parse(String raw) {
        InstrumentFile f = new InstrumentFile();
        String[] lines = raw.split("\r?\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String body = line.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    f.header.put(body.substring(0, colon).trim().toLowerCase(),
                                 body.substring(colon + 1).trim());
                }
                continue;
            }
            String[] cols = line.split(",");
            if (cols.length != 3) {
                f.issues.add(new ParseIssue(i + 1, line, "expected 3 columns time_s,temp_c,heatflow_mw"));
                continue;
            }
            try {
                double t = Double.parseDouble(cols[0].trim());
                double temp = Double.parseDouble(cols[1].trim());
                double hf = Double.parseDouble(cols[2].trim());
                if (Double.isNaN(t) || Double.isNaN(temp) || Double.isNaN(hf)
                        || Double.isInfinite(t) || Double.isInfinite(temp) || Double.isInfinite(hf)) {
                    f.issues.add(new ParseIssue(i + 1, line, "non-finite value kept in raw archive only"));
                    continue;
                }
                f.points.add(new DscPoint(t, temp, hf));
            } catch (NumberFormatException e) {
                f.issues.add(new ParseIssue(i + 1, line, "unconvertible numeric value kept in raw archive only"));
            }
        }
        return f;
    }

    public Optional<String> convention() {
        return Optional.ofNullable(header.get("convention"));
    }
}
