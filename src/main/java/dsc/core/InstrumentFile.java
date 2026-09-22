package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析仪器 CSV 输出。原始文本在导入层原样保留；
 * 这里只做结构化读取，单位规范化由派生层负责。
 */
public final class InstrumentFile {

    public record RawRow(double time, double temp, double heatFlow, int segment) {}

    public final Map<String, String> header = new LinkedHashMap<>();
    public final List<RawRow> rows = new ArrayList<>();
    public final List<String> diagnostics = new ArrayList<>();
    public String timeUnit = "s";
    public String tempUnit = "C";
    public String heatFlowUnit = "mW";

    public static InstrumentFile parse(String raw) {
        InstrumentFile f = new InstrumentFile();
        String[] lines = raw.split("\\R");
        boolean columnsSeen = false;
        int segIdx = -1;
        for (String line : lines) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) {
                String body = s.substring(1).trim();
                int colon = body.indexOf(':');
                if (colon > 0) {
                    f.header.put(body.substring(0, colon).trim(), body.substring(colon + 1).trim());
                }
                continue;
            }
            String[] parts = s.split(",");
            if (!columnsSeen) {
                // 列头行：识别单位与可选 segment 列
                for (int i = 0; i < parts.length; i++) {
                    String c = parts[i].trim().toLowerCase();
                    if (c.startsWith("time")) f.timeUnit = unitOf(c, "s");
                    if (c.startsWith("temp")) f.tempUnit = unitOf(c, "C");
                    if (c.startsWith("heatflow")) f.heatFlowUnit = unitOf(c, "mW");
                    if (c.equals("segment")) segIdx = i;
                }
                columnsSeen = true;
                continue;
            }
            try {
                double t = Double.parseDouble(parts[0].trim());
                double temp = Double.parseDouble(parts[1].trim());
                double hf = Double.parseDouble(parts[2].trim());
                int seg = segIdx >= 0 && segIdx < parts.length ? Integer.parseInt(parts[segIdx].trim()) : 1;
                f.rows.add(new RawRow(t, temp, hf, seg));
            } catch (RuntimeException e) {
                f.diagnostics.add("UNPARSEABLE_ROW: " + s);
            }
        }
        return f;
    }

    private static String unitOf(String col, String dflt) {
        int i = col.indexOf('_');
        return i >= 0 ? col.substring(i + 1) : dflt;
    }

    public String headerOr(String key, String dflt) {
        return header.getOrDefault(key, dflt);
    }
}
