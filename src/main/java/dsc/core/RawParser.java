package dsc.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for the offline DSC text format (fixtures ship in the repository).
 *
 * <pre>
 * file: NETZSCH_DSC_214.txt
 * instrument: NETZSCH DSC 214
 * temp-unit: C
 * heatflow-unit: mW
 * time-unit: s
 * rate: 10 K/min
 * atmosphere: N2, 50 mL/min
 * ---
 * # series segment=heat cycle=1 sign=exo-up
 * temp,heatflow,time
 * 30.0,-0.12,0
 * ...
 * </pre>
 *
 * Segment direction and exo/end convention come only from declared headers.
 */
public final class RawParser {

    private RawParser() {}

    public static RawScan parse(String fileName, String content) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<String, String> headers = new LinkedHashMap<>();
        List<String[]> blocks = new ArrayList<>();
        List<String> blockHeaders = new ArrayList<>();

        String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int i = 0;
        while (i < lines.length && !lines[i].strip().equals("---")) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon > 0 && !line.strip().startsWith("#")) {
                headers.put(line.substring(0, colon).trim().toLowerCase(),
                        line.substring(colon + 1).trim());
            }
            i++;
        }
        i++; // skip separator

        StringBuilder block = new StringBuilder();
        String blockHeader = "";
        for (; i < lines.length; i++) {
            String line = lines[i];
            if (line.strip().startsWith("# series")) {
                if (!block.isEmpty()) {
                    blocks.add(block.toString().split("\n"));
                    blockHeaders.add(blockHeader);
                    block.setLength(0);
                }
                blockHeader = line.strip();
            } else {
                block.append(line).append('\n');
            }
        }
        if (!block.isEmpty()) {
            blocks.add(block.toString().split("\n"));
            blockHeaders.add(blockHeader);
        }

        String tUnit = headers.getOrDefault("temp-unit", "C");
        String hfUnit = headers.getOrDefault("heatflow-unit", "mW");
        String tmUnit = headers.getOrDefault("time-unit", "s");
        ensureKnown(tUnit, "温度", diagnostics);
        ensureKnown(hfUnit, "热流", diagnostics);
        ensureKnown(tmUnit, "时间", diagnostics);

        List<RawSeries> series = new ArrayList<>();
        for (int b = 0; b < blocks.size(); b++) {
            RawSeries s = parseSeries(blockHeaders.get(b), blocks.get(b),
                    tUnit, hfUnit, tmUnit, diagnostics);
            if (s != null) {
                series.add(s);
            }
        }

        return new RawScan(
                fileName,
                headers.get("instrument"),
                tUnit,
                hfUnit,
                tmUnit,
                headers.get("rate"),
                headers.get("atmosphere"),
                List.copyOf(series),
                List.copyOf(diagnostics));
    }

    private static void ensureKnown(String unit, String what, List<Diagnostic> out) {
        if (!Units.known(unit)) {
            out.add(Diagnostic.of(DiagnosticCode.UNCONVERTIBLE_UNIT,
                    what + "使用了无法识别的单位「" + unit + "」，原值保留在来源档案",
                    "rules/v1#unit-registry", "unit:" + unit));
        }
    }

    private static RawSeries parseSeries(String headerLine, String[] rows,
                                         String tUnit, String hfUnit, String tmUnit,
                                         List<Diagnostic> diagnostics) {
        Map<String, String> meta = new LinkedHashMap<>();
        String h = headerLine.replaceFirst("^#\\s*series\\s*", "").trim();
        for (String part : h.split("\\s+")) {
            int eq = part.indexOf('=');
            if (eq > 0) {
                meta.put(part.substring(0, eq).trim().toLowerCase(), part.substring(eq + 1).trim());
            }
        }
        String seriesId = meta.getOrDefault("id",
                meta.getOrDefault("segment", "seg") + "-c" + meta.getOrDefault("cycle", "?"));
        String segment = meta.getOrDefault("segment", "unknown");
        String direction = meta.getOrDefault("direction", segment);
        int cycle = parseIntOr(meta.get("cycle"), 1);
        HeatSign sign = HeatSign.fromToken(meta.get("sign"));
        if (sign == HeatSign.UNDECLARED) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.UNKNOWN_CONVENTION,
                    "系列 " + seriesId + " 未声明吸放热方向，不能按文件名猜测",
                    "rules/v1#heat-sign", seriesId));
        }

        List<double[]> points = new ArrayList<>();
        int badRows = 0;
        for (String raw : rows) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("temp") || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length < 3) {
                badRows++;
                continue;
            }
            try {
                double tv = Double.parseDouble(parts[0].trim());
                double hv = Double.parseDouble(parts[1].trim());
                double sv = Double.parseDouble(parts[2].trim());
                points.add(new double[]{tv, hv, sv});
            } catch (NumberFormatException e) {
                badRows++;
            }
        }
        int n = points.size();
        double[] tK = new double[n];
        double[] hfW = new double[n];
        double[] dtS = new double[n];
        for (int k = 0; k < n; k++) {
            double[] p = points.get(k);
            tK[k] = normalizeTemp(p[0], tUnit);
            hfW[k] = normalizePower(p[1], hfUnit, diagnostics);
            dtS[k] = normalizeTime(p[2], tmUnit, diagnostics);
        }
        boolean nonUniform = detectNonUniform(tK);
        if (nonUniform) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.NON_UNIFORM_TEMPERATURE,
                    "系列 " + seriesId + " 温度采样非等间隔",
                    "rules/v1#non-uniform", seriesId));
        }
        if (!monotonic(tK, direction)) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.NON_MONOTONIC_TEMPERATURE,
                    "系列 " + seriesId + " 温度轴与声明方向 " + direction + " 不一致",
                    "rules/v1#monotonic", seriesId));
        }
        if (badRows > 0) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.UNCONVERTIBLE_UNIT,
                    "系列 " + seriesId + " 有 " + badRows + " 行无法解析，已保留原始文件并跳过",
                    "rules/v1#row-parse", seriesId));
        }
        return new RawSeries(seriesId, segment, direction, cycle, sign, tK, hfW, dtS, nonUniform);
    }

    private static double normalizeTemp(double v, String unit) {
        try {
            return Quantity.of(v, unit).toBase();
        } catch (UnconvertibleValueException e) {
            return Double.NaN;
        }
    }

    private static double normalizePower(double v, String unit, List<Diagnostic> diagnostics) {
        try {
            return Quantity.of(v, unit).toBase();
        } catch (UnconvertibleValueException e) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.UNCONVERTIBLE_UNIT,
                    "热流单位无法转换，保留原值 " + v + " " + unit,
                    "rules/v1#unit-registry", "unit:" + unit));
            return v;
        }
    }

    private static double normalizeTime(double v, String unit, List<Diagnostic> diagnostics) {
        try {
            return Quantity.of(v, unit).toBase();
        } catch (UnconvertibleValueException e) {
            diagnostics.add(Diagnostic.of(DiagnosticCode.UNCONVERTIBLE_UNIT,
                    "时间单位无法转换，保留原值 " + v + " " + unit,
                    "rules/v1#unit-registry", "unit:" + unit));
            return v;
        }
    }

    private static boolean detectNonUniform(double[] tK) {
        if (tK.length < 3) {
            return false;
        }
        double[] diffs = new double[tK.length - 1];
        double sum = 0;
        for (int k = 0; k < diffs.length; k++) {
            diffs[k] = Math.abs(tK[k + 1] - tK[k]);
            sum += diffs[k];
        }
        double mean = sum / diffs.length;
        if (mean == 0) {
            return false;
        }
        for (double d : diffs) {
            if (d > mean * 1.8 || d < mean * 0.4) {
                return true;
            }
        }
        return false;
    }

    private static boolean monotonic(double[] tK, String direction) {
        if (tK.length < 2) {
            return true;
        }
        boolean heat = "heat".equalsIgnoreCase(direction);
        boolean cool = "cool".equalsIgnoreCase(direction);
        if (!heat && !cool) {
            return true;
        }
        int violations = 0;
        for (int k = 1; k < tK.length; k++) {
            double d = tK[k] - tK[k - 1];
            if (heat && d < -1e-9) {
                violations++;
            }
            if (cool && d > 1e-9) {
                violations++;
            }
        }
        return violations <= 1;
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
