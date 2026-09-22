package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** 基线候选：多个合理基线全部保留差异，不为得到单一答案吞掉多解。 */
public final class Baselines {

    public record Interval(double onsetT, double endsetT) {
        public boolean overlaps(Interval other) {
            return this.onsetT < other.endsetT && other.onsetT < this.endsetT;
        }
    }

    /** 用户选定的切线：过 (t0, h0)、斜率 slope（mW/°C）。 */
    public record Tangent(double t0, double h0, double slope) {
        public double at(double t) { return h0 + slope * (t - t0); }
    }

    public record Tangents(Tangent left, Tangent right) {}

    /**
     * 候选基线。type:
     *  linear  —— onset/endset 端点直线, coeffs=[hOnset, slope]
     *  tangent —— 用户左右切线在交点拼接, coeffs=[tCross, leftT0,leftH0,leftSlope, rightT0,rightH0,rightSlope]
     *  step    —— 转变前后平台在中点阶跃（玻璃化）, coeffs=[tMid, hBefore, hAfter]
     */
    public record Candidate(String type, Interval interval, double[] coeffs, String note) {}

    private Baselines() {}

    public static double valueAt(Candidate c, double tempC) {
        switch (c.type()) {
            case "linear" -> {
                return c.coeffs()[0] + c.coeffs()[1] * (tempC - c.interval().onsetT());
            }
            case "tangent" -> {
                double[] k = c.coeffs();
                if (tempC <= k[0]) return k[2] + k[3] * (tempC - k[1]);
                return k[5] + k[6] * (tempC - k[4]);
            }
            case "step" -> {
                return tempC <= c.coeffs()[0] ? c.coeffs()[1] : c.coeffs()[2];
            }
            default -> throw new IllegalArgumentException("unknown baseline type " + c.type());
        }
    }

    /** 段内温度 tempC 处的热流插值（非等间隔温度安全）。 */
    public static double interpHf(Trace trace, Segmenter.Segment seg, double tempC) {
        List<Trace.Point> pts = trace.points;
        Trace.Point prev = pts.get(seg.fromPoint());
        for (int i = seg.fromPoint(); i <= seg.toPoint(); i++) {
            Trace.Point p = pts.get(i);
            boolean between = (prev.tempC() <= tempC && tempC <= p.tempC())
                    || (p.tempC() <= tempC && tempC <= prev.tempC());
            if (between) {
                double dt = p.tempC() - prev.tempC();
                if (Math.abs(dt) < 1e-12) return p.heatFlowMw();
                double f = (tempC - prev.tempC()) / dt;
                return prev.heatFlowMw() + f * (p.heatFlowMw() - prev.heatFlowMw());
            }
            prev = p;
        }
        return prev.heatFlowMw();
    }

    /** 生成全部合理候选：linear 总是可行；tangent 需要用户切线；step 用于玻璃化类台阶。 */
    public static List<Candidate> candidates(Trace trace, Segmenter.Segment seg, Interval iv, Tangents tangents) {
        List<Candidate> out = new ArrayList<>();
        double hOn = interpHf(trace, seg, iv.onsetT());
        double hEn = interpHf(trace, seg, iv.endsetT());
        double slope = (hEn - hOn) / (iv.endsetT() - iv.onsetT());
        out.add(new Candidate("linear", iv, new double[]{hOn, slope}, "endpoint straight line"));

        if (tangents != null && tangents.left() != null && tangents.right() != null) {
            Tangent l = tangents.left();
            Tangent r = tangents.right();
            if (Math.abs(l.slope() - r.slope()) > 1e-12) {
                double tCross = (r.h0() - r.slope() * r.t0() - l.h0() + l.slope() * l.t0())
                        / (l.slope() - r.slope());
                out.add(new Candidate("tangent", iv,
                        new double[]{tCross, l.t0(), l.h0(), l.slope(), r.t0(), r.h0(), r.slope()},
                        "user tangents joined at intersection"));
            }
        }

        double tMid = 0.5 * (iv.onsetT() + iv.endsetT());
        out.add(new Candidate("step", iv, new double[]{tMid, hOn, hEn},
                "pre/post level step at interval midpoint"));
        return out;
    }
}
