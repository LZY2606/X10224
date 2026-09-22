package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/** 相变积分：onset/peak/endset、焓变与误差来源。非等间隔温度用时间轴梯形积分。 */
public final class Integrator {

    public record ErrorSource(String source, double magnitude, String unit, String artifactRef) {}

    public record Result(
            String status,            // ok | empty | ambiguous_direction
            String direction,         // endo | exo | ambiguous
            double onsetT, double peakT, double endsetT,
            double peakHfMw,
            double areaMj,
            double enthalpyJg,        // NaN 当质量缺失
            List<ErrorSource> errors,
            List<String> diagnostics) {}

    private Integrator() {}

    public static Result integrate(Trace trace, Segmenter.Segment seg,
                                   Baselines.Candidate baseline,
                                   List<Baselines.Candidate> allCandidates,
                                   double massMg, double massUncertaintyMg,
                                   double calibrationOffsetC,
                                   String signConvention, String baselineArtifactRef) {
        List<String> diagnostics = new ArrayList<>();
        List<ErrorSource> errors = new ArrayList<>();
        Baselines.Interval iv = baseline.interval();

        // 收集区间内样点（按段内温度过滤，不跨段）
        List<Trace.Point> inside = new ArrayList<>();
        double lo = Math.min(iv.onsetT(), iv.endsetT());
        double hi = Math.max(iv.onsetT(), iv.endsetT());
        for (int i = seg.fromPoint(); i <= seg.toPoint(); i++) {
            Trace.Point p = trace.points.get(i);
            if (p.tempC() >= lo - 1e-9 && p.tempC() <= hi + 1e-9) inside.add(p);
        }
        if (inside.size() < 2) {
            diagnostics.add("empty interval: fewer than 2 sample points between onset "
                    + iv.onsetT() + " and endset " + iv.endsetT());
            return new Result("empty", "ambiguous", iv.onsetT(), Double.NaN, iv.endsetT(),
                    Double.NaN, 0.0, Double.NaN, errors, diagnostics);
        }

        // 时间轴梯形积分（mW * s = mJ），非等间隔温度安全
        double areaMj = 0.0;
        double peakAbs = -1.0;
        double peakT = Double.NaN;
        double peakHf = Double.NaN;
        double prevT = Double.NaN;
        double prevResid = 0.0;
        for (Trace.Point p : inside) {
            double resid = p.heatFlowMw() - Baselines.valueAt(baseline, p.tempC());
            if (!Double.isNaN(prevT)) {
                areaMj += 0.5 * (resid + prevResid) * (p.timeS() - prevT);
            }
            if (Math.abs(resid) > peakAbs) {
                peakAbs = Math.abs(resid);
                peakT = p.tempC();
                peakHf = p.heatFlowMw();
            }
            prevT = p.timeS();
            prevResid = resid;
        }

        // 吸放热方向：由残差符号 + 仪器 signConvention 决定；约定缺失时保持 ambiguous
        String direction;
        String status = "ok";
        if (signConvention == null) {
            direction = "ambiguous";
            status = "ambiguous_direction";
            diagnostics.add("signConvention missing in instrument metadata; "
                    + "endo/exo direction cannot be guessed from file name — both readings kept");
        } else {
            boolean upIsExo = signConvention.equals("exo_up");
            boolean residualPositive = areaMj > 0;
            // exo_up: 正残差为放热；endo_up: 正残差为吸热
            direction = (residualPositive == upIsExo) ? "exo" : "endo";
        }

        double enthalpyJg = Double.NaN;
        if (massMg > 0) {
            enthalpyJg = areaMj / massMg; // mJ/mg == J/g
        } else {
            diagnostics.add("sample mass missing; normalized enthalpy unavailable");
        }

        // 误差来源：并列基线差异
        double minE = Double.POSITIVE_INFINITY;
        double maxE = Double.NEGATIVE_INFINITY;
        for (Baselines.Candidate c : allCandidates) {
            double a = areaOf(trace, seg, c, inside);
            double e = massMg > 0 ? a / massMg : a;
            minE = Math.min(minE, e);
            maxE = Math.max(maxE, e);
        }
        if (allCandidates.size() > 1) {
            errors.add(new ErrorSource("baseline_choice_spread", maxE - minE, "J/g", baselineArtifactRef));
        }
        if (massMg > 0 && massUncertaintyMg > 0) {
            errors.add(new ErrorSource("sample_mass_uncertainty",
                    Math.abs(enthalpyJg) * massUncertaintyMg / massMg, "J/g", "mass"));
        }
        if (calibrationOffsetC > 0) {
            errors.add(new ErrorSource("temperature_calibration_offset", calibrationOffsetC, "C", "temp_calibration"));
        }

        return new Result(status, direction, iv.onsetT(), peakT, iv.endsetT(),
                peakHf, areaMj, enthalpyJg, errors, diagnostics);
    }

    private static double areaOf(Trace trace, Segmenter.Segment seg,
                                 Baselines.Candidate baseline, List<Trace.Point> inside) {
        double area = 0.0;
        double prevT = Double.NaN;
        double prevResid = 0.0;
        for (Trace.Point p : inside) {
            double resid = p.heatFlowMw() - Baselines.valueAt(baseline, p.tempC());
            if (!Double.isNaN(prevT)) {
                area += 0.5 * (resid + prevResid) * (p.timeS() - prevT);
            }
            prevT = p.timeS();
            prevResid = resid;
        }
        return area;
    }
}
