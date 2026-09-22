package dsc.rule;

import java.util.ArrayList;
import java.util.List;

/**
 * 玻璃化台阶分析（ASTM E1356 风格）：
 * onset/endset 为前、后平台切线与拐点切线的交点；中点为两交点的温度中点；
 * ΔCp 由热流台阶高度（后平台切线 − 前平台切线 @中点温度）除以扫描速率并按质量归一。
 */
public final class TgAnalysis {

    public record Mark(double tK, String label) {
    }

    public record Result(
            Mark onset,
            Mark midpoint,
            Mark endset,
            double deltaCpJPerGK,
            double stepHeightW,
            BaselineModel baseline,
            String method) {
    }

    private TgAnalysis() {
    }

    public static Result analyze(List<XY> curve, CurveMath.Line pre, CurveMath.Line post,
                                 FeatureWindow win, BaselineModel baseline,
                                 double massG, double rateKPerMin) {
        double tMid = 0.5 * (win.startK() + win.endK());
        double yPre = pre.y(tMid);
        double yPost = post.y(tMid);
        double step = yPost - yPre;

        List<XY> inside = CurveMath.slice(curve, win.startK(), win.endK());
        List<XY> centerBand = new ArrayList<>();
        double centerLo = win.startK() + 0.30 * win.widthK();
        double centerHi = win.startK() + 0.70 * win.widthK();
        for (XY p : inside) {
            if (p.x() >= centerLo && p.x() <= centerHi) {
                centerBand.add(p);
            }
        }
        if (centerBand.size() < 2) {
            centerBand = inside.size() >= 2 ? inside.subList(
                    Math.max(0, inside.size() / 2 - 2),
                    Math.min(inside.size(), inside.size() / 2 + 3)) : List.of();
        }
        CurveMath.Line inflectionLine = centerBand.size() >= 2
                ? CurveMath.fitLine(centerBand)
                : new CurveMath.Line(0.5 * (yPre + yPost), 0.0);

        Double tOn = intersection(pre, inflectionLine);
        Double tOff = intersection(post, inflectionLine);
        double onset = clamp(tOn != null ? tOn : win.startK(), win);
        double endset = clamp(tOff != null ? tOff : win.endK(), win);
        double midpoint = 0.5 * (onset + endset);

        double deltaCp = (step / (rateKPerMin / 60.0)) / massG;
        return new Result(new Mark(onset, "onset"), new Mark(midpoint, "midpoint"),
                new Mark(endset, "endset"), deltaCp, step, baseline, baseline.method());
    }

    private static Double intersection(CurveMath.Line a, CurveMath.Line b) {
        if (Math.abs(a.slope() - b.slope()) < 1e-15) {
            return null;
        }
        return (b.intercept() - a.intercept()) / (a.slope() - b.slope());
    }

    private static double clamp(double t, FeatureWindow win) {
        return Math.max(win.startK(), Math.min(win.endK(), t));
    }
}
