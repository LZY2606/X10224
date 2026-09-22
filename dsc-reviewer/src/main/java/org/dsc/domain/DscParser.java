package org.dsc.domain;

import java.util.ArrayList;
import java.util.List;
import org.dsc.domain.Models.*;

/**
 * 仪器输出解析器。格式为本案例包自定义但贴近真实仪器导出的纯文本：
 *
 *   SEGMENT seq=1 direction=heating convention=exo-up rate="10 K/min" atmosphere="N2,50mL/min"
 *   # time(s), temperature, heat flow
 *   0.0, 300.0 K, 0.12 mW/mg
 *   ...
 *
 * 关键语义：
 *  - direction/convention 只能从头字段读取；缺失即 UNKNOWN，后续计算拒绝猜测。
 *  - 每个值保留原始文本；无法规范化的行不丢弃，进 diagnostics 且不生成 Point。
 *  - 非等间隔温度只做检测与标记，不做重采样。
 */
public final class DscParser {
    private DscParser() {}

    public static final class ParsedSegment {
        public final Segment segment;
        public final List<String> rawLines;
        public final List<ParseDiagnostic> parseDiagnostics;
        ParsedSegment(Segment s, List<String> rawLines, List<ParseDiagnostic> diag) {
            this.segment = s;
            this.rawLines = rawLines;
            this.parseDiagnostics = diag;
        }
    }

    public static ParsedSegment parse(String segmentId, int seqNo, String block) {
        List<String> rawLines = new ArrayList<>();
        List<String> diag = new ArrayList<>();
        List<ParseDiagnostic> parseDiag = new ArrayList<>();
        List<Point> points = new ArrayList<>();

        ScanDirection direction = ScanDirection.UNKNOWN;
        HeatFlowConvention convention = HeatFlowConvention.UNKNOWN;
        String rateRaw = "";
        String atmoRaw = "";

        for (String line : block.split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.startsWith("#")) continue;
            rawLines.add(line);
            if (trimmed.toUpperCase().startsWith("SEGMENT")) {
                direction = parseDirection(headerValue(trimmed, "direction"));
                convention = parseConvention(headerValue(trimmed, "convention"));
                rateRaw = unquote(headerValue(trimmed, "rate"));
                atmoRaw = unquote(headerValue(trimmed, "atmosphere"));
                continue;
            }
            String[] cols = trimmed.split(",", -1);
            if (cols.length < 3) {
                diag.add("malformed row (need 3 columns): " + trimmed);
                parseDiag.add(new ParseDiagnostic("row:" + rawLines.size(), trimmed, "time,temp,heatflow",
                        "expected 3 comma-separated columns"));
                continue;
            }
            String tRaw = cols[0].trim();
            String tempRaw = cols[1].trim();
            String hfRaw = cols[2].trim();
            double time;
            try {
                time = Double.parseDouble(tRaw.replaceAll("[^0-9eE+\\-.]", ""));
                if (tRaw.toLowerCase().contains("min")) time *= 60.0;
            } catch (NumberFormatException e) {
                parseDiag.add(new ParseDiagnostic("row:" + rawLines.size(), trimmed, "s", "bad time column"));
                diag.add("bad time column: " + trimmed);
                continue;
            }
            Units.Conversion temp = Units.toKelvin(tempRaw);
            Units.Conversion hf = Units.heatFlowToWPerG(hfRaw);
            if (!temp.ok() || !hf.ok()) {
                if (!temp.ok()) parseDiag.add(new ParseDiagnostic("row:" + rawLines.size() + ".temp",
                        tempRaw, "K", temp.diagnostic()));
                if (!hf.ok()) parseDiag.add(new ParseDiagnostic("row:" + rawLines.size() + ".heatflow",
                        hfRaw, "W/g", hf.diagnostic()));
                diag.add("unconverted row kept in source: " + trimmed
                        + " [" + (!temp.ok() ? temp.diagnostic() : "")
                        + (!hf.ok() ? ";" + hf.diagnostic() : "") + "]");
                continue;
            }
            points.add(new Point(points.size(), time, temp.value(), hf.value()));
        }

        if (direction == ScanDirection.UNKNOWN)
            diag.add("segment direction missing in instrument header; must not be inferred from filename");
        if (convention == HeatFlowConvention.UNKNOWN)
            diag.add("heat-flow sign convention missing; endo/exo classification blocked");

        boolean nonUniform = detectNonUniform(points);
        if (nonUniform) diag.add("non-uniform temperature spacing detected; integration must not assume constant dt/dT");

        double rate = Units.parseRateKPerMin(rateRaw).orElse(Double.NaN);
        ScanRate scanRate = new ScanRate(rate, rateRaw, rateRaw.replaceAll("[0-9 .+\\-eE]+", "").trim());
        Segment seg = new Segment(segmentId, seqNo, direction, convention, scanRate,
                List.copyOf(points), nonUniform, List.copyOf(diag));
        return new ParsedSegment(seg, rawLines, parseDiag);
    }

    private static ScanDirection parseDirection(String v) {
        if (v == null) return ScanDirection.UNKNOWN;
        return switch (v.trim().toLowerCase()) {
            case "heating", "heat", "up" -> ScanDirection.HEATING;
            case "cooling", "cool", "down" -> ScanDirection.COOLING;
            default -> ScanDirection.UNKNOWN;
        };
    }

    private static HeatFlowConvention parseConvention(String v) {
        if (v == null) return HeatFlowConvention.UNKNOWN;
        return switch (v.trim().toLowerCase()) {
            case "exo-up", "exoup", "exotherm-up" -> HeatFlowConvention.EXO_UP;
            case "endo-up", "endoup", "endotherm-up" -> HeatFlowConvention.ENDO_UP;
            default -> HeatFlowConvention.UNKNOWN;
        };
    }

    private static String headerValue(String header, String key) {
        for (String part : header.split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equalsIgnoreCase(key)) return part.substring(eq + 1);
        }
        return null;
    }

    private static String unquote(String s) {
        if (s == null) return "";
        return s.replaceAll("^\"|\"$", "");
    }

    /** 相邻温度间隔相对标准差超过阈值即视为非等间隔。 */
    static boolean detectNonUniform(List<Point> points) {
        if (points.size() < 4) return false;
        double[] d = new double[points.size() - 1];
        double mean = 0;
        for (int i = 1; i < points.size(); i++) {
            d[i - 1] = Math.abs(points.get(i).temperature() - points.get(i - 1).temperature());
            mean += d[i - 1];
        }
        mean /= d.length;
        if (mean == 0) return true;
        double var = 0;
        for (double x : d) var += (x - mean) * (x - mean);
        double cv = Math.sqrt(var / d.length) / mean;
        return cv > 0.25;
    }
}
