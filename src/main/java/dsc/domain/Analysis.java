package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 相变事件分析：在一个温度区间（必须落在单一升/降温段内）上生成多个合理基线候选，
 * 分别给出 onset/peak/endset 与焓变，并列出焓变误差来源。
 * 多个合理基线全部保留，不为了单一答案吞掉多解。
 */
public final class Analysis {
    private Analysis() {}

    /**
     * @param temp       归一化温度数组（°C）
     * @param hf         归一化热流数组（W/g）
     * @param segStart/segEnd 所属段的下标范围（闭区间），积分绝不跨段
     * @param t1/t2      用户拖动的温度区间（°C，t1 < t2）
     * @param rateKs     扫描速率（K/s）
     * @param userTangent 用户选定切线 {slope, intercept}（归一化坐标），可空
     * @param tempUnc    温度标定不确定度（°C），可空
     * @param massRelUnc 质量相对不确定度，可空
     * @param directionKnown 吸放热方向是否已知（未知时不标注 endo/exo）
     */
    public static Map<String, Object> analyze(double[] temp, double[] hf, int segStart, int segEnd,
                                              double t1, double t2, Double rateKs,
                                              Map<String, Object> userTangent,
                                              Double tempUnc, Double massRelUnc,
                                              boolean directionKnown) {
        List<Map<String, Object>> diags = new ArrayList<>();
        // 区间裁剪到段内：循环段切换处绝不混用
        double segT1 = Math.min(temp[segStart], temp[segEnd]);
        double segT2 = Math.max(temp[segStart], temp[segEnd]);
        double lo = Math.max(t1, segT1), hi = Math.min(t2, segT2);
        boolean clipped = lo > t1 || hi < t2;
        if (clipped) {
            diags.add(Curves.diag("SEGMENT_BOUNDARY",
                    "区间跨越升降温段边界，已裁剪到段内 [" + r2(lo) + ", " + r2(hi) + "] °C，加热与冷却段不混用"));
        }
        List<Integer> idx = new ArrayList<>();
        for (int i = segStart; i <= segEnd; i++) {
            double t = temp[i];
            if (t >= lo && t <= hi) idx.add(i);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("window", Map.of("t1", t1, "t2", t2, "unit", "°C"));
        out.put("clippedWindow", Map.of("t1", lo, "t2", hi, "unit", "°C"));
        out.put("clipped", clipped);

        if (idx.size() < 3) {
            diags.add(Curves.diag("EMPTY_INTERVAL",
                    "区间内数据点不足（" + idx.size() + " 个），无法建立基线；区间保留在原位置"));
            out.put("diagnostics", diags);
            out.put("candidates", List.of());
            return out;
        }

        int n = idx.size();
        double[] T = new double[n], Y = new double[n];
        for (int i = 0; i < n; i++) {
            T[i] = temp[idx.get(i)];
            Y[i] = hf[idx.get(i)];
        }

        // 噪声估计：取区间前 15% 点对左端切线的残差
        double noise = estimateNoise(T, Y);

        List<Map<String, Object>> candidates = new ArrayList<>();
        candidates.add(candidate("LINEAR", linearBase(T[0], Y[0], T[n - 1], Y[n - 1]), T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown));
        double[] leftFit = fitLine(firstK(T, Y, Math.max(3, n / 5)));
        candidates.add(candidate("LEFT_TANGENT", leftFit, T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown));
        double[] rightFit = fitLine(lastK(T, Y, Math.max(3, n / 5)));
        candidates.add(candidate("RIGHT_TANGENT", rightFit, T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown));
        double[] sigmoid = fitSigmoid(T, Y);
        if (sigmoid != null) {
            candidates.add(candidate("SIGMOID", sigmoid, T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown, true));
        }
        if (userTangent != null && userTangent.get("slope") != null && userTangent.get("intercept") != null) {
            double s = ((Number) userTangent.get("slope")).doubleValue();
            double c = ((Number) userTangent.get("intercept")).doubleValue();
            candidates.add(candidate("USER_TANGENT", new double[]{s, c}, T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown));
        }

        // 焓变误差来源之一：并列基线之间的离散度
        double hmin = Double.POSITIVE_INFINITY, hmax = Double.NEGATIVE_INFINITY;
        for (Map<String, Object> c : candidates) {
            Object h = c.get("enthalpyJg");
            if (h instanceof Number) {
                hmin = Math.min(hmin, ((Number) h).doubleValue());
                hmax = Math.max(hmax, ((Number) h).doubleValue());
            }
        }
        double spread = (hmax - hmin);
        for (Map<String, Object> c : candidates) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> errs = (List<Map<String, Object>>) c.get("errorSources");
            errs.add(0, Map.of("source", "baseline-spread", "magnitudeJg", r4(spread),
                    "note", "并列基线候选间焓变差，多解不合并"));
        }

        out.put("candidates", candidates);
        out.put("diagnostics", diags);
        out.put("pointCount", n);
        return out;
    }

    private static Map<String, Object> candidate(String kind, double[] base, double[] T, double[] Y,
                                                 Double rateKs, double noise,
                                                 Double tempUnc, Double massRelUnc, boolean directionKnown) {
        return candidate(kind, base, T, Y, rateKs, noise, tempUnc, massRelUnc, directionKnown, false);
    }

    private static Map<String, Object> candidate(String kind, double[] base, double[] T, double[] Y,
                                                 Double rateKs, double noise,
                                                 Double tempUnc, Double massRelUnc,
                                                 boolean directionKnown, boolean sigmoid) {
        int n = T.length;
        double[] r = new double[n];
        for (int i = 0; i < n; i++) r[i] = Y[i] - baseValue(kind, base, T[i], sigmoid);

        // 峰：残差的局部极值，幅度超过 3 倍噪声；重叠转折保留全部峰
        List<Map<String, Object>> peaks = new ArrayList<>();
        double thresh = Math.max(3 * noise, 1e-9);
        for (int i = 1; i < n - 1; i++) {
            boolean max = r[i] >= r[i - 1] && r[i] >= r[i + 1] && r[i] > thresh;
            boolean min = r[i] <= r[i - 1] && r[i] <= r[i + 1] && -r[i] > thresh;
            if (max || min) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("temperature", new Units.Qty(r2(T[i]), "°C", "peak").json());
                p.put("residual", new Units.Qty(r4(r[i]), "W/g", "peak-residual").json());
                if (directionKnown) p.put("type", r[i] > 0 ? "ENDOTHERMIC" : "EXOTHERMIC");
                else p.put("type", "UNKNOWN_DIRECTION");
                peaks.add(p);
            }
        }
        if (peaks.size() > 1) {
            // 重叠转折：不合并、不取舍
        }

        // onset/endset：主蜂两侧残差首次穿越阈值的位置
        double onset = T[0], endset = T[n - 1];
        int mainPeak = 0;
        double best = 0;
        for (int i = 0; i < n; i++) {
            if (Math.abs(r[i]) > best) { best = Math.abs(r[i]); mainPeak = i; }
        }
        for (int i = mainPeak; i >= 0; i--) {
            if (Math.abs(r[i]) <= thresh) { onset = T[i]; break; }
        }
        for (int i = mainPeak; i < n; i++) {
            if (Math.abs(r[i]) <= thresh) { endset = T[i]; break; }
        }

        // 焓变：梯形积分（非等间隔温度安全），∫residual dT / rate。
        // rate 带符号：冷却段为负，保证 ΔH = ∫Φdt 的符号约定在升降温段一致。
        Double enthalpy = null;
        if (rateKs != null && rateKs != 0) {
            double area = 0;
            for (int i = 1; i < n; i++) {
                area += 0.5 * (r[i] + r[i - 1]) * (T[i] - T[i - 1]);
            }
            enthalpy = area / rateKs; // J/g
        }

        List<Map<String, Object>> errs = new ArrayList<>();
        if (tempUnc != null) {
            errs.add(Map.of("source", "temperature-calibration", "magnitudeC", tempUnc,
                    "note", "onset/endset 温度随标定不确定度平移"));
        }
        if (massRelUnc != null && enthalpy != null) {
            errs.add(Map.of("source", "sample-mass", "magnitudeJg", r4(Math.abs(enthalpy) * massRelUnc),
                    "note", "质量不确定度按比例进入归一化焓变"));
        }
        if (rateKs == null) {
            errs.add(Map.of("source", "scan-rate", "note", "扫描速率缺失，焓变不可计算"));
        }

        Map<String, Object> c = new LinkedHashMap<>();
        c.put("kind", kind);
        c.put("baseline", baselineJson(kind, base, sigmoid));
        c.put("onset", new Units.Qty(r2(onset), "°C", "extrapolated-onset").json());
        c.put("peak", new Units.Qty(r2(T[mainPeak]), "°C", "peak").json());
        c.put("endset", new Units.Qty(r2(endset), "°C", "extrapolated-endset").json());
        if (enthalpy != null) c.put("enthalpyJg", r4(enthalpy));
        c.put("peaks", peaks);
        c.put("overlapping", peaks.size() > 1);
        c.put("errorSources", errs);
        return c;
    }

    private static Map<String, Object> baselineJson(String kind, double[] base, boolean sigmoid) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        if (sigmoid) {
            m.put("y0", base[0]); m.put("step", base[1]); m.put("tm", base[2]); m.put("w", base[3]);
        } else {
            m.put("slope", base[0]);
            m.put("intercept", base[1]);
        }
        m.put("basis", "normalized-W/g-vs-°C");
        return m;
    }

    public static double baseValue(String kind, double[] base, double t, boolean sigmoid) {
        if (sigmoid) {
            return base[0] + base[1] / (1.0 + Math.exp(-(t - base[2]) / base[3]));
        }
        return base[0] * t + base[1];
    }

    /** 供 UI 绘制基线：对给定温度数组求基线值。 */
    public static List<Double> baselineValues(Map<String, Object> baseline, List<Double> temps) {
        String kind = (String) baseline.get("kind");
        boolean sigmoid = "SIGMOID".equals(kind);
        double[] b = sigmoid
                ? new double[]{num(baseline.get("y0")), num(baseline.get("step")), num(baseline.get("tm")), num(baseline.get("w"))}
                : new double[]{num(baseline.get("slope")), num(baseline.get("intercept"))};
        List<Double> out = new ArrayList<>(temps.size());
        for (double t : temps) out.add(baseValue(kind, b, t, sigmoid));
        return out;
    }

    private static double num(Object o) { return ((Number) o).doubleValue(); }

    private static double[] linearBase(double t0, double y0, double t1, double y1) {
        double s = (y1 - y0) / (t1 - t0);
        return new double[]{s, y0 - s * t0};
    }

    private static double[][] firstK(double[] T, double[] Y, int k) {
        double[] t = new double[k], y = new double[k];
        System.arraycopy(T, 0, t, 0, k);
        System.arraycopy(Y, 0, y, 0, k);
        return new double[][]{t, y};
    }

    private static double[][] lastK(double[] T, double[] Y, int k) {
        int n = T.length;
        double[] t = new double[k], y = new double[k];
        System.arraycopy(T, n - k, t, 0, k);
        System.arraycopy(Y, n - k, y, 0, k);
        return new double[][]{t, y};
    }

    private static double[] fitLine(double[][] pts) {
        double[] T = pts[0], Y = pts[1];
        int n = T.length;
        double st = 0, sy = 0, stt = 0, sty = 0;
        for (int i = 0; i < n; i++) {
            st += T[i]; sy += Y[i]; stt += T[i] * T[i]; sty += T[i] * Y[i];
        }
        double denom = n * stt - st * st;
        if (Math.abs(denom) < 1e-12) return new double[]{0, sy / n};
        double s = (n * sty - st * sy) / denom;
        return new double[]{s, (sy - s * st) / n};
    }

    /** 玻璃化台阶的 S 形基线：网格搜索中点与宽度，取最小残差平方和。 */
    private static double[] fitSigmoid(double[] T, double[] Y) {
        int n = T.length;
        double y0 = Y[0], y1 = Y[n - 1];
        double step = y1 - y0;
        double[] widths = {0.5, 1, 2, 4, 8};
        double bestSse = Double.POSITIVE_INFINITY;
        double[] best = null;
        for (int mi = 1; mi < n - 1; mi++) {
            double tm = T[mi];
            for (double w : widths) {
                double sse = 0;
                for (int i = 0; i < n; i++) {
                    double v = y0 + step / (1.0 + Math.exp(-(T[i] - tm) / w));
                    sse += (Y[i] - v) * (Y[i] - v);
                }
                if (sse < bestSse) { bestSse = sse; best = new double[]{y0, step, tm, w}; }
            }
        }
        return best;
    }

    private static double estimateNoise(double[] T, double[] Y) {
        int k = Math.max(3, (int) (T.length * 0.15));
        double[][] head = firstK(T, Y, k);
        double[] fit = fitLine(head);
        double ss = 0;
        for (int i = 0; i < k; i++) {
            double d = Y[i] - (fit[0] * T[i] + fit[1]);
            ss += d * d;
        }
        return Math.sqrt(ss / k);
    }

    private static double r2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static double r4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
