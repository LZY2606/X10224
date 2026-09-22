package dsc.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 基线候选生成。任何方法都不做单点“正确答案”保证；合法候选全部返回，
 * 并在适用条件不满足时返回诊断而不是悄悄替换方法。
 */
public final class Baselines {

    public record Candidate(String key, String label, BaselineModel model, List<String> rationale,
                            List<String> applicability) {
    }

    public record Result(List<Candidate> candidates, List<String> diagnostics) {
    }

    public static final String CHORD = "chord";
    public static final String TANGENT = "tangent";
    public static final String SIGMOID = "sigmoid-step";
    public static final String SPLIT_VALLEY = "split-valley";

    private Baselines() {
    }

    /**
     * 为峰类事件生成基线候选。anchorBandK 为窗口两侧各取的锚定带宽。
     */
    public static Result peakBaselines(List<XY> curve, FeatureWindow win, double anchorBandK) {
        List<String> diag = new ArrayList<>();
        if (win.isEmptyAcross(curve)) {
            throw new CurveMath.EmptyIntervalException(win.startK(), win.endK());
        }
        List<XY> leftBand = takeBand(curve, win.startK() - anchorBandK, win.startK(), true);
        List<XY> rightBand = takeBand(curve, win.endK(), win.endK() + anchorBandK, false);
        if (leftBand.size() < 2 || rightBand.size() < 2) {
            diag.add("锚定带点数不足（左 " + leftBand.size() + " / 右 " + rightBand.size()
                    + "），弦线基线仍可由窗口端点生成，切线基线不可用");
        }

        double yStart = CurveMath.interpolateY(curve, win.startK());
        double yEnd = CurveMath.interpolateY(curve, win.endK());
        CurveMath.Line chord = new CurveMath.Line(yStart, (yEnd - yStart) / win.widthK());

        List<Candidate> out = new ArrayList<>();
        out.add(new Candidate(CHORD, "窗口端点弦线基线",
                new BaselineModel.LineBaseline(CHORD, win.startK(), win.endK(), chord),
                List.of("连接积分窗口两端观测点", "不依赖锚定带，端点漂移敏感"),
                List.of("峰窗口")));

        boolean overlapping = looksOverlapping(CurveMath.slice(curve, win.startK(), win.endK()));
        if (leftBand.size() >= 2 && rightBand.size() >= 2) {
            CurveMath.Line tl = CurveMath.fitLine(leftBand);
            CurveMath.Line tr = CurveMath.fitLine(rightBand);
            double yAtStart = Math.max(tl.y(win.startK()), chord.y(win.startK()));
            double yAtEnd = Math.max(tr.y(win.endK()), chord.y(win.endK()));
            CurveMath.Line tangentConservative = new CurveMath.Line(yAtStart,
                    (yAtEnd - yAtStart) / win.widthK());
            out.add(new Candidate(TANGENT, "双侧切线保守包络基线",
                    new BaselineModel.LineBaseline(TANGENT, win.startK(), win.endK(), tangentConservative),
                    List.of("两侧锚定带线性外推", "取切线与弦线的保守包络，避免把拖尾计入焓"),
                    List.of("孤立峰", overlapping ? "重叠峰下会低估总焓，仅作对照" : "单峰首选")));

            CurveMath.Line tangentRaw = new CurveMath.Line(tl.y(win.startK()),
                    (tr.y(win.endK()) - tl.y(win.startK())) / win.widthK());
            out.add(new Candidate("tangent-extrapolated", "双侧切线直接外推基线",
                    new BaselineModel.LineBaseline("tangent-extrapolated", win.startK(), win.endK(), tangentRaw),
                    List.of("两侧锚定带线性外推，直接连接外推值"),
                    List.of("基线倾斜显著时与保守包络差异最大")));
        }

        if (overlapping) {
            addSplitValley(out, curve, win, chord, diag);
        } else {
            diag.add("窗口内未检出双峰结构，不提供分裂谷基线候选");
        }
        return new Result(out, diag);
    }

    /**
     * 玻璃化台阶基线候选：S 形台阶 + 双切线台阶。
     */
    public static Result stepBaselines(List<XY> curve, FeatureWindow win, double anchorBandK) {
        if (win.isEmptyAcross(curve)) {
            throw new CurveMath.EmptyIntervalException(win.startK(), win.endK());
        }
        List<XY> leftBand = takeBand(curve, win.startK() - anchorBandK, win.startK(), true);
        List<XY> rightBand = takeBand(curve, win.endK(), win.endK() + anchorBandK, false);
        if (leftBand.size() < 2 || rightBand.size() < 2) {
            throw new IllegalArgumentException("台阶基线要求两侧至少各 2 个锚定带点");
        }
        CurveMath.Line pre = CurveMath.fitLine(leftBand);
        CurveMath.Line post = CurveMath.fitLine(rightBand);
        double yLow = pre.y(0.5 * (win.startK() + win.endK()));
        double yHigh = post.y(0.5 * (win.startK() + win.endK()));
        double tMid = 0.5 * (win.startK() + win.endK());

        List<Candidate> out = new ArrayList<>();
        out.add(new Candidate(SIGMOID, "S 形台阶基线",
                new BaselineModel.SigmoidStepBaseline(win.startK(), win.endK(), yLow, yHigh, tMid,
                        win.widthK()),
                List.of("前/后平台切线定台阶高度", "中点过中点温度，宽度取窗口宽度"),
                List.of("玻璃化台阶")));

        double yAtStart = pre.y(win.startK());
        double yAtEnd = post.y(win.endK());
        CurveMath.Line twoTangent = new CurveMath.Line(yAtStart,
                (yAtEnd - yAtStart) / win.widthK());
        out.add(new Candidate("two-tangent-step", "双切线线性台阶基线",
                new BaselineModel.LineBaseline("two-tangent-step", win.startK(), win.endK(), twoTangent),
                List.of("前/后切线在窗口端点的直接连接，台阶被线性化"),
                List.of("玻璃化台阶", "ΔCp 与 S 形候选可能不同，差异保留")));
        return new Result(List.copyOf(out), List.of());
    }

    private static void addSplitValley(List<Candidate> out, List<XY> curve, FeatureWindow win,
                                       CurveMath.Line chord, List<String> diag) {
        List<XY> inside = CurveMath.slice(curve, win.startK(), win.endK());
        List<Integer> maxima = localMaxima(inside);
        if (maxima.size() < 2) {
            diag.add("疑似重叠但未找到两个局部极大，分裂谷候选放弃");
            return;
        }
        int i1 = maxima.get(0);
        int i2 = maxima.get(1);
        int valley = i1 + 1;
        for (int i = i1 + 1; i < i2; i++) {
            if (inside.get(i).y() < inside.get(valley).y()) {
                valley = i;
            }
        }
        double tv = inside.get(valley).x();
        double yv = inside.get(valley).y();
        double ys = chord.y(win.startK());
        double ye = chord.y(win.endK());
        CurveMath.Line l1 = new CurveMath.Line(ys, (yv - ys) / (tv - win.startK()));
        CurveMath.Line l2 = new CurveMath.Line(yv, (ye - yv) / (win.endK() - tv));
        BaselineModel splitModel = new BaselineModel() {
            @Override
            public String method() {
                return SPLIT_VALLEY;
            }

            @Override
            public double startK() {
                return win.startK();
            }

            @Override
            public double endK() {
                return win.endK();
            }

            @Override
            public double evaluate(double tK) {
                return tK <= tv ? l1.y(tK) : l2.y(tK);
            }
        };
        out.add(new Candidate(SPLIT_VALLEY, "谷值分裂基线（重叠双峰各一）",
                splitModel,
                List.of("两局部极大之间取谷值作为分界", "两个峰分别扣基线，与合并弦线总焓互为候选"),
                List.of("重叠转折", "合并/分裂都是合法候选，不得强制唯一")));
    }

    /** 简单可重复判据：两个局部极大且中间谷深超过峰幅 8%。 */
    public static boolean looksOverlapping(List<XY> inside) {
        List<Integer> maxima = localMaxima(inside);
        if (maxima.size() < 2) {
            return false;
        }
        int hi1 = maxima.get(0);
        int hi2 = maxima.get(1);
        int valley = hi1 + 1;
        for (int i = hi1 + 1; i < hi2; i++) {
            if (inside.get(i).y() < inside.get(valley).y()) {
                valley = i;
            }
        }
        double peakTop = Math.max(inside.get(hi1).y(), inside.get(hi2).y());
        double baselineRef = Math.min(
                inside.stream().mapToDouble(XY::y).min().orElse(peakTop),
                Math.min(inside.get(0).y(), inside.get(inside.size() - 1).y()));
        double amplitude = peakTop - baselineRef;
        return amplitude > 0 && (peakTop - inside.get(valley).y()) > 0.08 * amplitude;
    }

    private static List<Integer> localMaxima(List<XY> p) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i < p.size() - 1; i++) {
            if (p.get(i).y() > p.get(i - 1).y() && p.get(i).y() >= p.get(i + 1).y()) {
                out.add(i);
            }
        }
        return out;
    }

    private static List<XY> takeBand(List<XY> curve, double lo, double hi, boolean fromLeft) {
        List<XY> band = new ArrayList<>(CurveMath.slice(curve, lo, hi));
        int cap = Math.min(6, band.size());
        if (band.size() <= 6) {
            return band;
        }
        return fromLeft ? band.subList(band.size() - cap, band.size())
                        : band.subList(0, cap);
    }
}
