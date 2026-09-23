package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪器输出文件的原始解析：保留原文，头部键值与数据点都按原始表示存放。
 * 这里不做任何单位换算或方向猜测。
 */
public final class InstrumentFile {
    public final Map<String, String> header = new LinkedHashMap<>();
    public final List<double[]> points = new ArrayList<>(); // time, temp, heatflow（原始单位）
    public final String rawText;

    private InstrumentFile(String rawText) {
        this.rawText = rawText;
    }

    public static InstrumentFile parse(String text) {
        InstrumentFile f = new InstrumentFile(text);
        for (String line : text.split("\\R")) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            if (s.startsWith("#")) {
                int c = s.indexOf(':');
                if (c > 1) f.header.put(s.substring(1, c).trim(), s.substring(c + 1).trim());
                continue;
            }
            String[] parts = s.split("[,;\\t ]+");
            if (parts.length < 3) continue;
            try {
                double t = Double.parseDouble(parts[0]);
                double temp = Double.parseDouble(parts[1]);
                double hf = Double.parseDouble(parts[2]);
                f.points.add(new double[]{t, temp, hf});
            } catch (NumberFormatException ignored) {
                // 列标题行等非数据行，原样跳过（原文仍保留在 rawText）
            }
        }
        return f;
    }
}
