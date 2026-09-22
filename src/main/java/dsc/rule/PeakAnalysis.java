package dsc.rule;

import dsc.model.ScanMode;
import java.util.ArrayList;
import java.util.List;

/**
 * onset / peak / endset 与焓积分。曲线为“吸热向上”。
 * 加热段峰正向；冷却段放热结晶峰为负向，起止点顺序按段方向语义处理，
 * 但加热与冷却段绝不混用。
 */
public final class PeakAnalysis {

    public record Mark(double tK, double signalW, double baselineW, String label) {
    }

    public record Enthalpy(
            double rawJ,
            double normalizedJPerG,
            double sign,
            double uncertaintyJPerG,
            List<ErrorTerm> errorTerms,
            int intervals,
            double maxStepK,
            double minStepK) {
    }

    public record ErrorTerm(String code, String source, double contributionJPerG, double fraction) {
    }

    public record Result(
            Mark onset,
            Mark peak,
            Mark endset,
            Enthalpy enthalpy,
            String eventDirection,
            String baselineMethod,
            List<String> notes) {
    }

    private PeakAnalysis() {
    }

    public record Inputs(
            List<XY> curve,
            FeatureWindow window,
            BaselineModel baseline,
            ScanMode mode,
            double massG,
            double rateKPerMin,
            double massRelativeUncertainty,
            double rateRelativeUncertainty,
            double baselineFraction) {

        public Inputs(List<XY> curve, FeatureWindow window, BaselineModel baseline, ScanMode mode,
                      double massG, double rateKPerMin) {
            this(curve, window, baseline, mode, massG, rateKPerMin, 0.002, 0.01, 0.02);
        }
    }

    public static Result analyze(Inputs in) {
        List<XY> curve = in.curve();
        FeatureWindow win = in.window();
        BaselineModel base = in.baseline();
        List<XY> inside = CurveMath.slice(curve, win.startK(), win.endK());
        if (inside.isEmpty()) {
            throw new CurveMath.EmptyIntervalException(win.startK(), win.endK());
        }

        List<XY> net = new ArrayList<>();
        for (XY p : inside) {
            net.add(new XY(p.x(), p.y() - base.evaluate(p.x())));
        }

        boolean heating = in.mode() == ScanMode.HEATING;
        double extremum = net.get(0).y();
        int extremumIdx = 0;
        for (int i = 1; i < net.size(); i++) {
            if ((heating && net.get(i).y() > extremum)
                    || (!heating && net.get(i).y() < extremum)) {
                extremum = net.get(i).y();
                extremumIdx = i;
            }
        }
        XY peakNet = net.get(extremumIdx);

        double sign = extremum >= 0 ? 1.0 : -1.0;
        String direction = sign > 0 ? "endothermic" : "exothermic";

        List<String> notes = new ArrayList<>();
        Mark onset = tangentMark(net, win, true, heating, notes);
        Mark endset = tangentMark(net, win, false, heating, notes);

        CurveMath.IntegralResult intRes = CurveMath.integrate(net, win.startK(), win.endK());
        double rawSignedJ = intRes.value() / (in.rateKPerMin() / 60.0);

        double norm = in.massG() > 0 ? 1.0 / in.massG() : Double.NaN;
        double hSigned = rawSignedJ * norm;
        double hAbs = Math.abs(hSigned);

        double[] abs = new double[4];
        List<ErrorTerm> terms = new ArrayList<>();
        abs[0] = hAbs * in.massRelativeUncertainty();
        terms.add(new ErrorTerm("MASS_UNCERTAINTY", "样品质量称量不确定度（质量版本 "
                + String.format("%.3f", in.massG()) + " g）", abs[0], 0));
        abs[1] = hAbs * in.rateRelativeUncertainty();
        terms.add(new ErrorTerm("RATE_UNCERTAINTY", "扫描速率不确定度（"
                + String.format("%.3f", in.rateKPerMin()) + " K/min，时间积分换能量所需）", abs[1], 0));
        abs[2] = in.baselineFraction() * hAbs;
        terms.add(new ErrorTerm("BASELINE_METHOD",
                "基线方法 " + base.method() + " 的方法差异（并列候选差异保留，不合并为单一答案）",
                abs[2], 0));
        abs[3] = Math.abs(intRes.trapErrorBound() / (in.rateKPerMin() / 60.0)) * Math.abs(norm);
        terms.add(new ErrorTerm("INTEGRATION_GRID",
                "非等间隔梯形截断误差（" + intRes.intervals() + " 个区间，最大步长 "
                        + String.format("%.3f", intRes.maxStepK()) + " K，最小 "
                        + String.format("%.3f", intRes.minStepK()) + " K）", abs[3], 0));

        double rss = 0;
        for (double v : abs) {
            rss += v * v;
        }
        List<ErrorTerm> withFrac = new ArrayList<>();
        for (ErrorTerm t : terms) {
            withFrac.add(new ErrorTerm(t.code(), t.source(), t.contributionJPerG(),
                    hAbs > 0 ? t.contributionJPerG() / hAbs : 0));
        }

        Mark peak = new Mark(peakNet.x(), peakNet.y() + base.evaluate(peakNet.x()),
                base.evaluate(peakNet.x()), "peak");
        Enthalpy enthalpy = new Enthalpy(rawSignedJ, hSigned, sign, Math.sqrt(rss), withFrac,
                intRes.intervals(), intRes.maxStepK(), intRes.minStepK());
        return new Result(onset, peak, endset, enthalpy, direction, base.method(), notes);
    }

    /**
     * onset/endset：各自半区找最陡点（5 点中心斜率），其切线（净信号坐标）
     * 与 y=0（即基线）的交点。夹到窗口边界时给出诊断说明。
     */
    private static Mark tangentMark(List<XY> net, FeatureWindow win, boolean wantOnset,
                                    boolean heating, List<String> notes) {
        int half = net.size() / 2;
        int from = wantOnset ? 0 : half + 1;
        int to = wantOnset ? half : net.size();

        double bestAbsSlope = -1;
        int best = wantOnset ? 0 : net.size() - 1;
        for (int i = from; i < to; i++) {
            int a = Math.max(0, i - 2);
            int b = Math.min(net.size() - 1, i + 2);
            double slope = (net.get(b).y() - net.get(a).y()) / (net.get(b).x() - net.get(a).x());
            if (Math.abs(slope) > bestAbsSlope) {
                bestAbsSlope = Math.abs(slope);
                best = i;
            }
        }
        int a = Math.max(0, best - 2);
        int b = Math.min(net.size() - 1, best + 2);
        double slope = (net.get(b).y() - net.get(a).y()) / (net.get(b).x() - net.get(a).x());
        double intercept = net.get(best).y() - slope * net.get(best).x();
        double t = Math.abs(slope) < 1e-18 ? net.get(best).x() : -intercept / slope;
        double clamped = Math.max(win.startK(), Math.min(win.endK(), t));
        if (clamped != t) {
            notes.add((wantOnset ? "onset" : "endset") + " 切线交点落在窗口外，已夹到窗口边界");
        }
        return new Mark(clamped, 0, 0, wantOnset ? "onset" : "endset");
    }
}
