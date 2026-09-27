package dsc.core;

import dsc.model.Direction;
import dsc.model.Quantity;
import dsc.model.RawPoint;
import dsc.model.Segment;
import dsc.model.SignConvention;

import java.util.ArrayList;
import java.util.List;

/**
 * 仪器输出解析器。头部为 key: value 的仪器元数据，数据列为 temp<TAB|,>heatflow。
 * 关键语义：
 *  - direction / sign_convention 只取自仪器头，绝不根据文件名推断；
 *  - 坏点保留原始 token 与诊断，不静默删除；
 *  - 单独校验温度单调性（非等间隔允许），反向序列必须由方向声明解释。
 */
public final class InstrumentFile {
    private InstrumentFile() {}

    public static Segment parse(String segmentId, String fileName, String content) {
        Segment seg = new Segment(segmentId, fileName);
        String[] lines = content.split("\\r?\\n");
        boolean inData = false;
        int pointIndex = 0;
        int observedSign = 0; // +1 升温, -1 降温（由数据本身观察）

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) continue;
            String lower = trimmed.toLowerCase();

            if (!inData && (lower.startsWith("temperature") || lower.startsWith("temp"))) {
                inData = true;
                continue;
            }
            if (!inData && trimmed.contains(":")) {
                int colon = trimmed.indexOf(':');
                String key = trimmed.substring(0, colon).trim().toLowerCase();
                String val = trimmed.substring(colon + 1).trim();
                switch (key) {
                    case "instrument" -> seg.instrument = val;
                    case "method", "segment", "procedure" -> seg.method = val;
                    case "direction", "scan_direction", "segment_type" -> {
                        Direction d = Direction.fromCode(val);
                        seg.declaredDirection = d;
                        if (d == Direction.UNKNOWN)
                            seg.diagnostics.add("direction header unrecognized: " + val);
                    }
                    case "sign_convention", "heatflow_sign", "sign", "heat_flow_sign" -> {
                        SignConvention sc = SignConvention.fromCode(val);
                        seg.declaredSign = sc;
                        if (sc == SignConvention.UNSPECIFIED)
                            seg.diagnostics.add("sign convention unrecognized: " + val);
                    }
                    case "rate", "scan_rate", "heating_rate", "cooling_rate" ->
                            seg.rate = Units.parseRate(val);
                    default -> { /* 忽略未知头，但不报错 */ }
                }
                continue;
            }
            if (!inData && (trimmed.contains(",") || trimmed.contains("\t"))) {
                inData = true;
            }
            if (!inData) continue;

            String[] parts = trimmed.split("[\\t,;]\\s*|\\s{2,}");
            if (parts.length < 2) {
                seg.diagnostics.add("line skipped (expected 2 columns): " + trimmed);
                continue;
            }
            RawPoint p = new RawPoint(pointIndex++, parts[0].trim(), parts[1].trim());
            Quantity t = Units.parseTemperature(p.tempRaw);
            Quantity h = Units.parseHeatFlow(p.hfRaw);
            p.tempC = t.value;
            p.hfMw = h.value;
            if (t.diagnostic != null || h.diagnostic != null) {
                p.diagnostic = String.join("; ", nz(t.diagnostic, h.diagnostic));
                seg.diagnostics.add("point#" + p.index + ": " + p.diagnostic);
            }
            seg.points.add(p);
        }

        List<RawPoint> usable = seg.usablePoints();
        int inc = 0, dec = 0;
        Double prev = null;
        for (RawPoint p : usable) {
            if (prev != null) {
                if (p.tempC > prev + 1e-9) inc++;
                else if (p.tempC < prev - 1e-9) dec++;
            }
            prev = p.tempC;
        }
        if (inc > dec) observedSign = 1;
        else if (dec > inc) observedSign = -1;

        if (seg.declaredDirection == Direction.UNKNOWN) {
            seg.diagnostics.add("direction_missing: direction not declared by instrument header; "
                    + "filename never used to infer endo/exo or heating/cooling");
        } else {
            if (observedSign == 1 && seg.declaredDirection == Direction.COOLING)
                seg.diagnostics.add("direction_conflict: header=cooling but temperature increases with scan order");
            if (observedSign == -1 && seg.declaredDirection == Direction.HEATING)
                seg.diagnostics.add("direction_conflict: header=heating but temperature decreases with scan order");
        }
        if (seg.declaredSign == SignConvention.UNSPECIFIED) {
            seg.diagnostics.add("sign_missing: endo/exo orientation unresolved; "
                    + "enthalpy sign left ambiguous until instrument sign is supplied");
        }
        if (seg.rate == null || !seg.rate.valid()) {
            seg.diagnostics.add("rate_missing: scan rate unavailable; rate-dependent error cannot be estimated");
        }
        return seg;
    }

    private static List<String> nz(String a, String b) {
        List<String> out = new ArrayList<>();
        if (a != null) out.add(a);
        if (b != null) out.add(b);
        return out;
    }
}
