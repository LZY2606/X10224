package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses an instrument export without losing anything: '#' header lines are
 * kept as both raw lines and key/value metadata, and any cell that cannot be
 * converted stays at its index with a diagnostic instead of dropping the row.
 */
public final class DscCsv {

    private DscCsv() {
    }

    public record ParsedColumns(int timeColumn, int temperatureColumn, int heatFlowColumn) {
    }

    public static InstrumentSeries parse(String runId, String fileName, String content) {
        String[] lines = content.split("\r?\n", -1);
        List<String> headers = new ArrayList<>();
        Map<String, String> metadata = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        List<String[]> rows = new ArrayList<>();
        List<Map<String, Object>> diagnostics = new ArrayList<>();

        int dataStart = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                headers.add(line);
                String body = trimmed.substring(1).trim();
                int eq = body.indexOf('=');
                if (eq > 0) {
                    metadata.putIfAbsent(body.substring(0, eq).trim(),
                            body.substring(eq + 1).trim());
                }
                dataStart = i + 1;
                continue;
            }
            if (trimmed.isEmpty()) {
                if (columns.isEmpty()) {
                    dataStart = i + 1;
                }
                continue;
            }
            String[] cells = splitCsv(line);
            if (columns.isEmpty()) {
                for (String c : cells) {
                    columns.add(c.trim());
                }
                dataStart = i + 1;
                continue;
            }
            rows.add(cells);
            dataStart = i + 1;
        }

        ParsedColumns pc = locateColumns(columns, metadata, diagnostics);
        double[] times = new double[rows.size()];
        Double[] temps = new Double[rows.size()];
        Double[] flows = new Double[rows.size()];

        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            double timeS = Double.NaN;
            if (pc.timeColumn() >= 0 && cells.length > pc.timeColumn()) {
                UnitValue tv = parseSeconds(cells[pc.timeColumn()]);
                if (tv.valid()) {
                    timeS = tv.si();
                } else {
                    diagnostic(diagnostics, r, "time", cells[pc.timeColumn()],
                            tv.parseError());
                }
            } else {
                timeS = r;
            }
            times[r] = timeS;

            if (pc.temperatureColumn() >= 0 && cells.length > pc.temperatureColumn()) {
                String raw = cells[pc.temperatureColumn()];
                UnitValue v = Units.parseTemperature(raw);
                temps[r] = v.valid() ? v.si() : null;
                if (!v.valid()) {
                    diagnostic(diagnostics, r, "temperature", raw, v.parseError());
                }
            }
            if (pc.heatFlowColumn() >= 0 && cells.length > pc.heatFlowColumn()) {
                String raw = cells[pc.heatFlowColumn()];
                UnitValue v = Units.parseHeatFlow(raw);
                flows[r] = v.valid() ? v.si() : null;
                if (!v.valid()) {
                    diagnostic(diagnostics, r, "heatFlow", raw, v.parseError());
                }
            }
        }

        if (pc.temperatureColumn() < 0) {
            diagnostic(diagnostics, -1, "temperature", null,
                    "no temperature column found");
        }
        if (pc.heatFlowColumn() < 0) {
            diagnostic(diagnostics, -1, "heatFlow", null,
                    "no heat-flow column found");
        }

        return new InstrumentSeries(runId, fileName, headers, metadata, columns, rows,
                pc.timeColumn(), pc.temperatureColumn(), pc.heatFlowColumn(),
                times, temps, flows, diagnostics);
    }

    private static ParsedColumns locateColumns(List<String> columns,
                                               Map<String, String> metadata,
                                               List<Map<String, Object>> diagnostics) {
        int time = -1;
        int temp = -1;
        int flow = -1;
        for (int i = 0; i < columns.size(); i++) {
            String c = columns.get(i).toLowerCase(Locale.ROOT);
            if (time < 0 && (c.contains("time") || c.equals("t") || c.contains("s)"))) {
                time = i;
            }
            if (temp < 0 && (c.contains("temp") || c.startsWith("t(")
                    || c.contains("°c") || c.contains("k)"))) {
                temp = i;
            }
            if (flow < 0 && (c.contains("heat") || c.contains("flow")
                    || c.contains("hf") || c.contains("dsc"))) {
                flow = i;
            }
        }
        String declared = metadata.get("heatflow-column");
        if (declared != null) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).equalsIgnoreCase(declared.trim())) {
                    flow = i;
                }
            }
        }
        if (time < 0) {
            diagnostic(diagnostics, -1, "time", null,
                    "no time column found; row index used");
        }
        return new ParsedColumns(time, temp, flow);
    }

    private static UnitValue parseSeconds(String raw) {
        String t = raw.trim().toLowerCase(Locale.ROOT);
        double scale = 1.0;
        String body = t;
        if (t.endsWith("min")) {
            scale = 60.0;
            body = t.substring(0, t.length() - 3);
        } else if (t.endsWith("s") && !t.isBlank()) {
            body = t.substring(0, t.length() - 1);
        }
        try {
            double v = Double.parseDouble(body.trim().replace(",", "."));
            return new UnitValue(raw, v * scale, scale == 60.0 ? "min" : "s", null);
        } catch (NumberFormatException e) {
            return new UnitValue(raw, null, "s", "cannot parse time: " + raw);
        }
    }

    private static void diagnostic(List<Map<String, Object>> out, int row,
                                   String field, String raw, String message) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("row", row);
        d.put("field", field);
        d.put("raw", raw);
        d.put("message", message);
        out.add(d);
    }

    static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
