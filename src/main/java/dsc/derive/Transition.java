package dsc.derive;

import dsc.domain.Entities.Direction;
import dsc.domain.Entities.ExoSign;
import dsc.domain.Values.Point;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次热转折的派生结果：玻璃化台阶 / 结晶 / 熔融。
 * 提供 onset、peak、endset 以及比焓(J/g)，并完整列出误差来源。
 * 所有结果带稳定身份（由依赖内容哈希得到）。
 */
public final class Transition {

    public enum Kind {
        GLASS("glass", "玻璃化"),
        CRYSTALLIZATION("crystallization", "结晶"),
        MELTING("melting", "熔融"),
        UNKNOWN("unknown", "未识别");
        public final String code;
        public final String label;

        Kind(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    public final String id;
    public final Kind kind;
    public final String segmentId;
    public final String baselineCandidateId;
    public final Double onsetK;
    public final Double peakK;
    public final Double endsetK;
    public final Double enthalpyJPerG;
    public final Double deltaCpJPerGPerK;
    public final boolean endothermic;
    public final double areaWSigned;
    public final List<String> diagnostics;
    public final List<Map<String, Object>> errorBudget;
    public final Map<String, String> sourceVersions;

    Transition(String id, Kind kind, String segmentId, String baselineCandidateId,
               Double onsetK, Double peakK, Double endsetK,
               Double enthalpyJPerG, Double deltaCp, boolean endothermic, double area,
               List<String> diagnostics, List<Map<String, Object>> errorBudget,
               Map<String, String> sourceVersions) {
        this.id = id;
        this.kind = kind;
        this.segmentId = segmentId;
        this.baselineCandidateId = baselineCandidateId;
        this.onsetK = onsetK;
        this.peakK = peakK;
        this.endsetK = endsetK;
        this.enthalpyJPerG = enthalpyJPerG;
        this.deltaCpJPerGPerK = deltaCp;
        this.endothermic = endothermic;
        this.areaWSigned = area;
        this.diagnostics = List.copyOf(diagnostics);
        this.errorBudget = List.copyOf(errorBudget);
        this.sourceVersions = Map.copyOf(sourceVersions);
    }

    /**
     * 计算一个区间 [fromK,toK] 内相对基线的偏差曲线与转折量。
     * @param ordered 已按温度升序、可用点
     * @param sign 仪器显式符号约定
     * @param direction 段方向（加热/冷却不混用）
     * @param massKg 样品质量（归一化）；NaN 则不给比焓
     * @param rateKPerMin 扫描速率（K/min）
     * @param expectedKind 用户/规则预期类型
     */
    public static Transition compute(String id, String segmentId, String baselineCandidateId,
                                     List<Point> ordered, ExoSign sign, Direction direction,
                                     Baseline bl, double fromK, double toK,
                                     double massKg, double rateKPerMin,
                                     Kind expectedKind, boolean emptyAllowed,
                                     Map<String, String> sourceVersions) {
        List<String> diag = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        List<Point> window = clip(ordered, fromK, toK);
        if (window.size() < 2) {
            if (!emptyAllowed) {
                diag.add("空区间：[" + fromK + "," + toK + "] 内有效点不足（" + window.size() + "），拒绝积分");
            } else {
                diag.add("空区间检验：显式允许空，返回零面积但标记不可用于焓结论");
            }
            return new Transition(id, expectedKind, segmentId, baselineCandidateId,
                    null, null, null, null, null, false, 0.0, diag,
                    List.of(term("empty_window", "空区间/点不足", Double.NaN)), sourceVersions);
        }

        // 偏差：向上=吸热坐标下相对基线
        double[] t = new double[window.size()];
        double[] dev = new double[window.size()];
        for (int i = 0; i < window.size(); i++) {
            Point p = window.get(i);
            t[i] = p.tempK;
            dev[i] = Baseline.toEndoUp(p.heatFlowW, sign) - bl.valueAt(p.tempK);
        }

        // 非等间隔诊断
        diag.addAll(nonUniform(t));

        // 峰与方向：找最大绝对偏差
        int peakIdx = 0;
        double maxAbs = 0;
        for (int i = 0; i < dev.length; i++) {
            if (Math.abs(dev[i]) > maxAbs) {
                maxAbs = Math.abs(dev[i]);
                peakIdx = i;
            }
        }
        boolean endothermic = dev[peakIdx] >= 0; // 吸热坐标
        double peakK = t[peakIdx];

        // onset/endset：阈值法（回到基线噪声带）
        double noise = robustNoise(dev);
        double thr = Math.max(noise * 3.0, maxAbs * 0.05);
        Double onset = walkToBaseline(t, dev, peakIdx, -1, thr);
        Double endset = walkToBaseline(t, dev, peakIdx, +1, thr);

        // 积分：逐段梯形，温度坐标(K) -> 时间(s) = dT / rate(K/s)
        double areaWSigned = 0.0;
        double rateKPerSec = rateKPerMin / 60.0;
        for (int i = 1; i < t.length; i++) {
            double dT = t[i] - t[i - 1];
            if (dT <= 0) {
                diag.add("出现非递增温度对 " + t[i - 1] + "->" + t[i] + "，该子区间跳过");
                continue;
            }
            if (!(rateKPerSec > 0)) {
                diag.add("扫描速率无效，无法把温度积分换算为时间积分");
                rateKPerSec = Double.NaN;
                break;
            }
            double dt = dT / rateKPerSec;
            areaWSigned += 0.5 * (dev[i] + dev[i - 1]) * dt; // J（W*s）
        }

        Double enthalpy = null;
        Double deltaCp = null;
        Kind kind = expectedKind;
        if (expectedKind == Kind.GLASS) {
            // 玻璃化台阶：用基线两侧值差求 ΔCp
            double dW = bl.rightValueW - bl.leftValueW;
            if (rateKPerSec > 0 && massKg > 0) {
                double dWdt = dW / rateKPerSec; // W = J/s，除以(K/s) => J/K
                deltaCp = dWdt / (massKg * 1000.0); // J/g/K
            } else {
                diag.add("质量/速率缺失，ΔCp 无法归一化");
            }
            // onset/endset 对台阶取基线两侧锚点
            onset = bl.leftAnchorK;
            endset = bl.rightAnchorK;
        } else {
            if (massKg > 0) {
                enthalpy = (areaWSigned / 1000.0) / (massKg * 1000.0); // J/g：area J / (kg*1000=g)
                // 上面 area 单位 J, massKg*1000 = g；J/g
                enthalpy = areaWSigned / (massKg * 1000.0);
            } else {
                diag.add("样品质量缺失或不可用，焓仅以绝对面积(J)保留，未归一化");
            }
            // 方向一致性校验：冷却段出现强吸热/加热段出现强放热时给诊断
            if (direction == Direction.HEATING && !endothermic
                    && expectedKind != Kind.UNKNOWN && expectedKind != Kind.GLASS) {
                diag.add("加热段检测到放热偏差，与预期 " + expectedKind.label + " 方向不一致（可能方向标记或符号约定错误）");
            }
            if (direction == Direction.COOLING && endothermic
                    && expectedKind != Kind.UNKNOWN && expectedKind != Kind.GLASS) {
                diag.add("冷却段检测到吸热偏差，与预期 " + expectedKind.label + " 方向不一致");
            }
            // 类型推断
            if (expectedKind == Kind.UNKNOWN) {
                kind = endothermic ? Kind.MELTING : Kind.CRYSTALLIZATION;
                if (direction == Direction.COOLING) {
                    kind = endothermic ? Kind.UNKNOWN : Kind.CRYSTALLIZATION;
                }
            }
        }

        // 误差来源预算
        errors.add(term("baseline_anchor", "基线锚点选取（左右切线位置）",
                anchorUncertainty(window, bl, rateKPerSec, massKg)));
        errors.add(term("temperature_nonuniformity", "非等间隔温度梯形截断",
                truncationError(t, dev, rateKPerSec, massKg)));
        errors.add(term("noise_threshold", "噪声阈值影响 onset/endset",
                onsetUncertaintyK(t, noise, maxAbs)));
        if (!(massKg > 0)) {
            errors.add(term("mass_missing", "质量缺失（未归一化）", Double.NaN));
        } else {
            errors.add(term("mass_version", "样品质量版本 " + sourceVersions.getOrDefault("massId", "?"),
                    0.0));
        }
        if (!(rateKPerMin > 0)) {
            errors.add(term("rate_invalid", "扫描速率无效", Double.NaN));
        }
        if (sign == ExoSign.UNKNOWN) {
            errors.add(term("exo_sign_unknown", "吸放热方向约定未知（不能靠文件名猜）", Double.NaN));
            diag.add("仪器未声明 exoSign，吸放热方向不可判定，已保留两种解释");
        }
        errors.add(term("calibration_offset", "温度标定偏移", 0.0));

        return new Transition(id, kind, segmentId, baselineCandidateId, onset, peakK, endset,
                enthalpy, deltaCp, endothermic, areaWSigned, diag, errors, sourceVersions);
    }

    private static List<Point> clip(List<Point> ordered, double lo, double hi) {
        List<Point> out = new ArrayList<>();
        for (Point p : ordered) {
            if (p.tempK >= lo - 1e-9 && p.tempK <= hi + 1e-9) {
                out.add(p);
            }
        }
        return out;
    }

    private static List<String> nonUniform(double[] t) {
        List<String> raw = new ArrayList<>();
        if (t.length < 3) {
            return raw;
        }
        double[] d = new double[t.length - 1];
        double sum = 0;
        for (int i = 1; i < t.length; i++) {
            d[i - 1] = t[i] - t[i - 1];
            sum += d[i - 1];
        }
        double mean = sum / d.length;
        double maxRel = 0;
        for (double v : d) {
            maxRel = Math.max(maxRel, Math.abs(v - mean) / mean);
        }
        if (maxRel > 0.25) {
            raw.add(String.format("非等间隔温度(最大偏差%.0f%%)，已使用逐段梯形积分", maxRel * 100));
        }
        return raw;
    }

    private static double robustNoise(double[] dev) {
        // 用一阶差分的中位绝对差估计噪声
        double[] dd = new double[Math.max(0, dev.length - 1)];
        for (int i = 1; i < dev.length; i++) {
            dd[i - 1] = Math.abs(dev[i] - dev[i - 1]);
        }
        if (dd.length == 0) {
            return 0;
        }
        java.util.Arrays.sort(dd);
        return dd[dd.length / 2] * 0.5;
    }

    private static Double walkToBaseline(double[] t, double[] dev, int start, int step, double thr) {
        int i = start;
        while (i + step >= 0 && i + step < t.length) {
            if (Math.abs(dev[i]) <= thr) {
                return t[i];
            }
            i += step;
        }
        return null;
    }

    private static double anchorUncertainty(List<Point> w, Baseline bl, double rateKPerSec, double massKg) {
        // 粗略：把基线端点上下移动一个端点偏差量级，对面积的相对影响（J/g）
        if (!(rateKPerSec > 0 && massKg > 0)) {
            return Double.NaN;
        }
        double spanK = Math.max(1e-9, bl.rightAnchorK - bl.leftAnchorK);
        double edge = Math.abs(bl.rightValueW - bl.leftValueW) + 1e-12;
        double j = edge * (spanK / rateKPerSec);
        return j / (massKg * 1000.0);
    }

    private static double truncationError(double[] t, double[] dev, double rate, double massKg) {
        if (!(rate > 0 && massKg > 0)) {
            return Double.NaN;
        }
        double e = 0;
        for (int i = 1; i < t.length; i++) {
            double dT = Math.max(0, t[i] - t[i - 1]);
            double second = Math.abs(dev[i] - dev[i - 1]);
            e += 0.5 * second * (dT / rate);
        }
        return e / (massKg * 1000.0);
    }

    private static double onsetUncertaintyK(double[] t, double noise, double maxAbs) {
        if (t.length < 2) {
            return Double.NaN;
        }
        double step = (t[t.length - 1] - t[0]) / (t.length - 1);
        return step * (maxAbs > 0 ? Math.min(1.0, 3 * noise / maxAbs) : 1.0);
    }

    private static Map<String, Object> term(String code, String label, double valueJPerG) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("label", label);
        if (Double.isNaN(valueJPerG) || Double.isInfinite(valueJPerG)) {
            m.put("magnitudeJPerG", null);
            m.put("usable", false);
        } else {
            m.put("magnitudeJPerG", valueJPerG);
            m.put("usable", true);
        }
        return m;
    }
}
