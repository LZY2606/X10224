package dsc.derive;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.Json;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses raw instrument CSV output. The raw text is always retained on the source row;
 * parsing happens in the derived layer. Cells that cannot be converted stay in place
 * (recorded as diagnostics with their raw text) instead of being silently dropped.
 */
public final class InstrumentParser {

    public record Parsed(Map<String, String> header, double[] timeS, double[] tempC, double[] heatflowMw,
            List<int[]> keptRows, ArrayNode diagnostics) {}

    public static Parsed parse(String raw) {
        Map<String, String> header = new LinkedHashMap<>();
        List<Double> time = new ArrayList<>();
        List<Double> temp = new ArrayList<>();
        List<Double> flow = new ArrayList<>();
        ArrayNode diagnostics = Json.M.createArrayNode();
        String[] lines = raw.split("\R");
        int lineNo = 0;
        for (String line : lines) {
            lineNo++;
            String s = line.trim();
            if (s.isEmpty()) {
                continue;
            }
            if (s.startsWith("#")) {
                String body = s.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    header.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                }
                continue;
            }
            String[] cols = s.split(",");
            if (cols.length < 3) {
                diagnostics.add(diag("ROW_UNPARSEABLE", lineNo, s, "列数不足，原始行保留在 raw 中"));
                continue;
            }
            try {
                double ts = Double.parseDouble(cols[0].trim());
                double tc = Double.parseDouble(cols[1].trim());
                double hf = Double.parseDouble(cols[2].trim());
                time.add(ts);
                temp.add(tc);
                flow.add(hf);
            } catch (NumberFormatException e) {
                ObjectNode d = diag("CELL_UNCONVERTIBLE", lineNo, s,
                        "热流单元无法转换为数值，原始行保留在 raw 中，派生层跳过该点");
                diagnostics.add(d);
            }
        }
        return new Parsed(header, toArray(time), toArray(temp), toArray(flow), List.of(), diagnostics);
    }

    private static ObjectNode diag(String code, int line, String raw, String note) {
        ObjectNode d = Json.obj();
        d.put("code", code);
        d.put("line", line);
        d.put("raw", raw);
        d.put("note", note);
        return d;
    }

    private static double[] toArray(List<Double> xs) {
        double[] out = new double[xs.size()];
        for (int i = 0; i < xs.size(); i++) {
            out[i] = xs.get(i);
        }
        return out;
    }

    private InstrumentParser() {}
}
