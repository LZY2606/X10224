package dsc.domain;

import java.util.Map;

/** 解析仪器输出 CSV。#key=value 为元数据；数据行 time_s,temp_C,heatflow_mW。 */
public final class TraceParser {

    private TraceParser() {}

    public static Trace parse(String csv) {
        Trace trace = new Trace();
        trace.rawText = csv;
        String[] lines = csv.split("\\R", -1);
        boolean headerSeen = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) {
                String body = trimmed.substring(1).trim();
                int eq = body.indexOf('=');
                if (eq > 0) {
                    trace.metadata.put(body.substring(0, eq).trim(), body.substring(eq + 1).trim());
                } else {
                    trace.metadata.put(body, "");
                }
                continue;
            }
            if (!headerSeen && trimmed.toLowerCase().startsWith("time")) {
                headerSeen = true;
                continue;
            }
            headerSeen = true;
            String[] cols = trimmed.split(",");
            if (cols.length < 3) {
                trace.diagnostics.add(new Trace.Diagnostic(i + 1, line,
                        "row has " + cols.length + " columns, expected 3; left in original position"));
                continue;
            }
            try {
                double t = Double.parseDouble(cols[0].trim());
                double temp = Double.parseDouble(cols[1].trim());
                double hf = Double.parseDouble(cols[2].trim());
                trace.points.add(new Trace.Point(t, temp, hf));
            } catch (NumberFormatException e) {
                trace.diagnostics.add(new Trace.Diagnostic(i + 1, line,
                        "unparseable numeric value; left in original position: " + e.getMessage()));
            }
        }
        return trace;
    }

    /** 吸放热方向只来自仪器元数据 signConvention，绝不靠文件名猜测。 */
    public static String signConvention(Map<String, String> metadata) {
        String v = metadata.get("signConvention");
        if (v == null) return null;
        v = v.trim().toLowerCase();
        if (v.equals("exo_up") || v.equals("exo-up") || v.equals("exoup")) return "exo_up";
        if (v.equals("endo_up") || v.equals("endo-up") || v.equals("endoup")) return "endo_up";
        return null;
    }
}
