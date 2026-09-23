package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 归一化派生层：把仪器原始输出换算到 W/g 对 °C 的坐标基准。
 * 吸放热方向只认仪器头部声明的 Convention，绝不靠文件名猜测；
 * 无法换算的量留在原位置并附诊断。
 */
public final class Curves {
    private Curves() {}

    public record Segment(int start, int end, String direction, int cycle) {
        public Map<String, Object> json() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("start", start);
            m.put("end", end);
            m.put("direction", direction);
            m.put("cycle", cycle);
            return m;
        }
    }

    public static Map<String, Object> diag(String code, String message) {
        return Units.diag(code, message, null);
    }

    /**
     * @param massG      样品质量（克），null 表示缺失
     * @param massRelUnc 质量相对不确定度，可空
     * @param calA/calB  温度标定 T_corr = a*T + b，可空
     * @param tempUnc    温度标定不确定度（°C），可空
     * @param rateKs     扫描速率（K/s），可空
     */
    public static Map<String, Object> normalize(InstrumentFile raw, Double massG, Double massRelUnc,
                                                Double calA, Double calB, Double tempUnc, Double rateKs) {
        List<Map<String, Object>> diags = new ArrayList<>();
        String convention = raw.header.getOrDefault("Convention", "");
        String direction;
        double sign;
        switch (convention) {
            case "endo_up": direction = "ENDO_UP"; sign = 1.0; break;
            case "exo_up": direction = "EXO_UP"; sign = -1.0; break;
            default:
                direction = "UNKNOWN";
                sign = 1.0;
                diags.add(diag("DIRECTION_UNKNOWN",
                        "仪器输出未声明吸放热方向约定（Convention），按原始信号保留，方向不做猜测"));
        }

        String hfUnit = raw.header.getOrDefault("HeatFlowUnit", "?");
        String tempUnit = raw.header.getOrDefault("TempUnit", "°C");
        Double hfToW = Units.scale(hfUnit, "W");
        boolean hfOk = hfToW != null;
        if (!hfOk) {
            diags.add(diag("UNCONVERTIBLE_UNIT", "热流单位 " + hfUnit + " 无法换算为 W，原始值保留在原位置"));
        }
        boolean massOk = massG != null && massG > 0;
        if (!massOk) {
            diags.add(diag("MASS_MISSING", "缺少有效样品质量，热流保留为未按质量归一的形式"));
        }

        int n = raw.points.size();
        List<Double> times = new ArrayList<>(n);
        List<Double> temps = new ArrayList<>(n);
        List<Double> hfs = new ArrayList<>(n);
        boolean tempOk = true;
        for (double[] p : raw.points) {
            times.add(p[0]);
            Double c = Units.toCelsius(p[1], tempUnit);
            if (c == null) {
                tempOk = false;
                temps.add(p[1]); // 原值留在原位置
            } else {
                double tc = c;
                if (calA != null && calB != null) tc = calA * tc + calB;
                temps.add(tc);
            }
            if (hfOk && massOk) {
                hfs.add(p[2] * hfToW * sign / massG);
            } else {
                hfs.add(p[2]); // 原值留在原位置
            }
        }
        if (!tempOk) {
            diags.add(diag("UNCONVERTIBLE_UNIT", "温度单位 " + tempUnit + " 无法换算为 °C，原始值保留在原位置"));
        }

        // 扫描速率：优先用来源文档；缺失时按 dT/dt 中位数估计（非等间隔温度安全）
        Double rate = rateKs;
        if (rate == null && n > 2) {
            List<Double> slopes = new ArrayList<>();
            for (int i = 1; i < n; i++) {
                double dt = times.get(i) - times.get(i - 1);
                if (dt > 0) slopes.add(Math.abs((temps.get(i) - temps.get(i - 1)) / dt));
            }
            if (!slopes.isEmpty()) {
                slopes.sort(Double::compareTo);
                rate = slopes.get(slopes.size() / 2);
                diags.add(diag("RATE_ESTIMATED", "扫描速率来源缺失，按温度-时间斜率中位数估计"));
            }
        }

        List<Segment> segments = segment(temps);
        // 循环段切换检查：相邻段方向交替时记录机器可读事件线索
        for (int i = 1; i < segments.size(); i++) {
            Segment prev = segments.get(i - 1), cur = segments.get(i);
            if (!prev.direction().equals(cur.direction()) && !"ISOTHERM".equals(cur.direction())) {
                diags.add(diag("CYCLE_SEGMENT_SWITCH",
                        "段 " + i + " 发生升降温切换（" + prev.direction() + " -> " + cur.direction()
                                + "，循环 " + cur.cycle() + "），积分不得跨段混用"));
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("time", times);
        out.put("temperature", temps);
        out.put("heatFlow", hfs);
        out.put("heatFlowUnit", hfOk && massOk ? "W/g" : hfUnit);
        out.put("heatFlowBasis", hfOk && massOk ? "sample-mass-normalized" : "instrument-raw");
        out.put("temperatureUnit", tempOk ? "°C" : tempUnit);
        out.put("temperatureBasis", tempOk ? "calibrated" : "instrument-raw");
        out.put("direction", direction);
        out.put("scanRate", rate == null ? null : new Units.Qty(rate, "K/s", "derived-or-source").json());
        if (massRelUnc != null) out.put("massRelUnc", massRelUnc);
        if (tempUnc != null) out.put("tempUnc", tempUnc);
        List<Map<String, Object>> segs = new ArrayList<>();
        for (Segment s : segments) segs.add(s.json());
        out.put("segments", segs);
        out.put("diagnostics", diags);
        return out;
    }

    /** 按温度单调性切段：HEATING / COOLING / ISOTHERM，循环号在升温重新开始时递增。 */
    public static List<Segment> segment(List<Double> temps) {
        List<Segment> out = new ArrayList<>();
        int n = temps.size();
        if (n == 0) return out;
        double eps = 1e-9;
        int start = 0;
        String dir = directionOf(temps, 0);
        for (int i = 1; i < n; i++) {
            String d = directionOf(temps, i);
            if (!d.equals(dir)) {
                out.add(new Segment(start, i, dir, 0));
                start = i;
                dir = d;
            }
        }
        out.add(new Segment(start, n - 1, dir, 0));
        int cycle = 1;
        String prevNonIso = null;
        List<Segment> result = new ArrayList<>();
        for (Segment s : out) {
            if ("HEATING".equals(s.direction()) && "COOLING".equals(prevNonIso)) cycle++;
            result.add(new Segment(s.start(), s.end(), s.direction(), cycle));
            if (!"ISOTHERM".equals(s.direction())) prevNonIso = s.direction();
        }
        return result;
    }

    private static String directionOf(List<Double> temps, int i) {
        int j = Math.min(i + 1, temps.size() - 1);
        double d = temps.get(j) - temps.get(Math.max(j - 1, 0));
        if (d > 1e-9) return "HEATING";
        if (d < -1e-9) return "COOLING";
        return "ISOTHERM";
    }
}
