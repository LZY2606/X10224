package dsc.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 领域量。每个量都携带单位或坐标基准，并保留导入时的原始表示。
 * 派生层才做规范化；无法转换的值留在原位置并附诊断，绝不为了画图而丢弃。
 */
public final class Values {

    private Values() {
    }

    /** 带单位、原始表示与（可能失败的）规范化的标量。 */
    public static final class Q {
        public final double value;
        public final String unit;
        public final String raw;
        public final Double normalized;
        public final String normalizeError;

        public Q(double value, String unit) {
            this(value, unit, null, null);
        }

        public Q(double value, String unit, String raw, String normalizeError) {
            this.value = value;
            this.unit = unit;
            this.raw = raw;
            this.normalized = normalizeError == null ? value : null;
            this.normalizeError = normalizeError;
        }

        public boolean isConvertible() {
            return normalizeError == null;
        }

        public Q withRaw(String r) {
            return new Q(value, unit, r, normalizeError);
        }

        @Override
        public String toString() {
            return value + " " + unit + (normalizeError != null ? " (!" + normalizeError + ")" : "");
        }
    }

    /** 一个采样点：温度（K）、热流（W，仪器符号约定）、可选时间（s）。 */
    public static final class Point {
        public final double tempK;
        public final double heatFlowW;
        public final Double timeS;
        /** 该点的原始文本表示（导入保真）。 */
        public final String rawTemp;
        public final String rawHeat;
        public final String rawTime;
        public final List<String> diagnostics;

        public Point(double tempK, double heatFlowW, Double timeS,
                     String rawTemp, String rawHeat, String rawTime,
                     List<String> diagnostics) {
            this.tempK = tempK;
            this.heatFlowW = heatFlowW;
            this.timeS = timeS;
            this.rawTemp = rawTemp;
            this.rawHeat = rawHeat;
            this.rawTime = rawTime;
            this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public boolean usable() {
            return !Double.isNaN(tempK) && !Double.isNaN(heatFlowW);
        }
    }

    public static double celsiusToKelvin(double c) {
        return c + 273.15;
    }

    /**
     * 解析温度。支持 K / °C / C / F；无法识别返回 NaN 并给诊断。
     */
    public static double parseTemperature(String raw, String declaredUnit, List<String> diag) {
        if (raw == null) {
            diag.add("温度缺失");
            return Double.NaN;
        }
        String s = raw.trim();
        String unit = declaredUnit == null ? "" : declaredUnit.trim();
        try {
            double v = Double.parseDouble(s.replaceAll("[^0-9eE+\\-.]", ""));
            String u = unit.toUpperCase();
            if (u.contains("KELVIN") || u.equals("K")) {
                return v;
            } else if (u.contains("FAHR") || u.equals("F") || u.equals("°F")) {
                return (v - 32.0) * 5.0 / 9.0 + 273.15;
            } else {
                // 默认摄氏（DSC 常见）
                return celsiusToKelvin(v);
            }
        } catch (NumberFormatException e) {
            diag.add("温度无法转换，保留原值: " + s);
            return Double.NaN;
        }
    }

    /**
     * 解析热流，规范化到 W。支持 W、mW、µW/uW。无法识别返回 NaN。
     */
    public static double parseHeatFlow(String raw, String declaredUnit, List<String> diag) {
        if (raw == null) {
            diag.add("热流缺失");
            return Double.NaN;
        }
        String s = raw.trim();
        String unit = declaredUnit == null ? "" : declaredUnit.trim();
        Double num = parseDouble(s);
        if (num == null) {
            diag.add("热流无法转换，保留原值: " + s);
            return Double.NaN;
        }
        String u = unit.toLowerCase();
        double scale = 1.0;
        if (u.equals("mw") || u.contains("milli")) {
            scale = 1e-3;
        } else if (u.equals("µw") || u.equals("uw") || u.contains("micro")) {
            scale = 1e-6;
        } else if (u.equals("w") || u.isEmpty()) {
            scale = 1.0;
        } else {
            diag.add("热流单位无法识别(" + unit + ")，按 W 处理并保留原值 " + s);
            scale = 1.0;
        }
        return num * scale;
    }

    /** 解析样品质量到 kg。支持 mg、g、kg、µg。 */
    public static double parseMassKg(String raw, List<String> diag) {
        if (raw == null) {
            diag.add("质量缺失");
            return Double.NaN;
        }
        String s = raw.trim();
        Double num = parseDouble(s.replaceAll("(?i)(mg|µg|ug|kg|g)$", "").trim());
        if (num == null) {
            diag.add("质量无法转换，保留原值: " + s);
            return Double.NaN;
        }
        String low = s.toLowerCase();
        if (low.endsWith("mg")) {
            return num * 1e-6;
        } else if (low.endsWith("µg") || low.endsWith("ug")) {
            return num * 1e-9;
        } else if (low.endsWith("kg")) {
            return num;
        } else if (low.endsWith("g")) {
            return num * 1e-3;
        } else {
            diag.add("质量单位缺失，按 g 处理: " + s);
            return num * 1e-3;
        }
    }

    public static double parseRateKPerMin(String raw, List<String> diag) {
        if (raw == null) {
            diag.add("扫描速率缺失");
            return Double.NaN;
        }
        Double num = parseDouble(raw.replaceAll("[^0-9eE+\\-.]", ""));
        if (num == null) {
            diag.add("扫描速率无法转换: " + raw);
            return Double.NaN;
        }
        return Math.abs(num); // 速率大小；方向由 direction 显式给出
    }

    private static Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 等间隔评估：相邻温度差相对中位差的最大相对偏差。返回诊断（空=等间隔）。 */
    public static List<String> nonUniformSpacing(List<Point> pts) {
        List<String> out = new ArrayList<>();
        List<Point> usable = usableInTempOrder(pts);
        if (usable.size() < 3) {
            return out;
        }
        double[] d = new double[usable.size() - 1];
        double sum = 0;
        for (int i = 1; i < usable.size(); i++) {
            d[i - 1] = usable.get(i).tempK - usable.get(i - 1).tempK;
            sum += d[i - 1];
        }
        double med = sum / d.length;
        if (med <= 0) {
            out.add("温度非单调递增，无法作为积分坐标");
            return out;
        }
        double maxRel = 0;
        int where = -1;
        for (int i = 0; i < d.length; i++) {
            double rel = Math.abs(d[i] - med) / med;
            if (rel > maxRel) {
                maxRel = rel;
                where = i;
            }
        }
        if (maxRel > 0.25) {
            out.add(String.format(
                    "非等间隔温度：最大间距偏差 %.0f%%（约 %.2f K 处），积分改按逐段梯形而非等距公式",
                    maxRel * 100, usable.get(where + 1).tempK));
        }
        return out;
    }

    /** 过滤可用点并要求温度严格递增；若输入顺序相反则按温度排序（冷却段同样在温度坐标上递增表示）。 */
    public static List<Point> usableInTempOrder(List<Point> pts) {
        List<Point> out = new ArrayList<>();
        for (Point p : pts) {
            if (p.usable()) {
                out.add(p);
            }
        }
        out.sort((a, b) -> Double.compare(a.tempK, b.tempK));
        return out;
    }

    public static boolean nearlyEqual(double a, double b) {
        return Math.abs(a - b) <= 1e-9 * Math.max(1.0, Math.abs(a) + Math.abs(b));
    }

    public static List<String> copyDiag(List<String> d) {
        return d == null ? new ArrayList<>() : new ArrayList<>(d);
    }

    public static String requireNonNullUnit(String unit, String fallback) {
        return Objects.requireNonNullElse(unit, fallback);
    }
}
