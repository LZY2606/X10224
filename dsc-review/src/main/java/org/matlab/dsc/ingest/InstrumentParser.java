package org.matlab.dsc.ingest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.matlab.dsc.domain.Diagnostic;
import org.matlab.dsc.domain.RawPoint;

/**
 * Parser for the repository case-pack instrument text format. It performs no
 * unit conversion and no sign inference: every raw representation survives
 * into storage so the normalised layer can be recomputed without losing the
 * instrument frame.
 *
 * Format:
 *   # comment
 *   header: key = raw value
 *   columns: i | t_raw | hf_raw | time_raw
 *   row: 0 | 300.15 K | 0.42 mW | 0 s
 *   diagnostic: position | CODE | raw | detail
 */
public final class InstrumentParser {

    public InstrumentFile parse(String origin, List<String> lines) {
        Map<String, String> header = new LinkedHashMap<>();
        List<RawPoint> points = new ArrayList<>();
        List<Diagnostic> diags = new ArrayList<>();

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("header:")) {
                String body = line.substring("header:".length()).trim();
                int eq = body.indexOf('=');
                if (eq > 0) {
                    header.put(body.substring(0, eq).trim(), body.substring(eq + 1).trim());
                }
            } else if (line.startsWith("row:")) {
                String[] cells = splitCells(line.substring("row:".length()));
                List<String> flags = new ArrayList<>();
                points.add(new RawPoint(points.size(), cells[1], cells[2], cells[3], flags));
            } else if (line.startsWith("diagnostic:")) {
                String[] cells = splitCells(line.substring("diagnostic:".length()));
                diags.add(new Diagnostic("diag:" + origin + ":" + diags.size(),
                        origin, cells[0], cells[1], cells[2],
                        cells.length > 3 ? cells[3] : ""));
                int idx = parseIndex(cells[0]);
                if (idx >= 0) {
                    flagRow(points, idx, cells[1]);
                }
            }
        }
        return new InstrumentFile(origin, header, points, diags);
    }

    private static int parseIndex(String position) {
        String digits = position.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? -1 : Integer.parseInt(digits);
    }

    private static void flagRow(List<RawPoint> points, int idx, String code) {
        if (idx < points.size()) {
            RawPoint p = points.get(idx);
            List<String> flags = new ArrayList<>(p.flags());
            flags.add(code);
            points.set(idx, new RawPoint(p.index(), p.rawTemperature(), p.rawHeatFlow(),
                    p.rawTime(), flags));
        }
    }

    private static String[] splitCells(String body) {
        String[] parts = body.split("\\|", -1);
        String[] out = new String[4];
        for (int i = 0; i < 4; i++) {
            out[i] = i < parts.length ? parts[i].trim() : "";
        }
        return out;
    }
}
