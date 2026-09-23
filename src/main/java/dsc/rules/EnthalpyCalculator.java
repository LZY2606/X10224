package dsc.rules;

import dsc.model.BaselineCandidate;
import dsc.model.Direction;
import dsc.model.RuleVersions;
import dsc.model.SamplePoint;
import dsc.model.Transition;

import java.util.ArrayList;
import java.util.List;

/**
 * Rule MASS-NORM-1 / DH-SIGN-1: converts the signed instrument integral
 * (K·W = J) into a specific enthalpy J/g using the current sample mass
 * version, and applies the thermal sign convention.
 *
 * <p>Endothermic transitions (melting, glass Cp step on heating) are
 * reported positive; exothermic crystallisation negative — on both heating
 * and cooling legs. The sign is derived from measured direction and peak
 * sense; a file name is never consulted.
 */
public final class EnthalpyCalculator {

    private EnthalpyCalculator() {}

    public record Result(double enthalpyJg, double errorJg, List<Transition.ErrorSource> budget) {}

    public static Result calculate(
            List<SamplePoint> points,
            BaselineCandidate baseline,
            boolean reversePolarity,
            boolean endothermic,
            double sampleMassMg,
            double scanRateKmin,
            double calibrationOffsetK,
            double massRelUncertainty,
            double rateRelUncertainty,
            double baselineUncertaintyW,
            double calOffsetUncertaintyK,
            int unparseableInside) {

        Integrator.RawResult raw = Integrator.integrate(points, baseline, reversePolarity);
        double widthK = baseline.rightTK() - baseline.leftTK();
        double energyJ = endothermic ? Math.abs(raw.energyJ()) : -Math.abs(raw.energyJ());

        double massG = sampleMassMg / 1000.0;
        double enthalpy = massG > 0 ? energyJ / massG : Double.NaN;

        List<Transition.ErrorSource> budget = new ArrayList<>();
        double widthSafe = Math.max(Math.abs(widthK), 1e-9);

        // 1. Mass revision / weighing uncertainty (normalisation factor).
        double eMass = Math.abs(enthalpy) * massRelUncertainty;
        budget.add(new Transition.ErrorSource("ERR_MASS",
                "样品质量称量/版本不确定度（质量修订会使所有归一化派生过期）",
                eMass, null));

        // 2. Scan rate: the signal is temperature-integrated; rate affects
        //    only time-derived cross checks, kept as a documented term.
        double eRate = Math.abs(enthalpy) * rateRelUncertainty * 0.0;
        budget.add(new Transition.ErrorSource("ERR_RATE",
                "扫描速率标定不确定度（温度积分中仅作交叉校验项）",
                eRate, null));

        // 3. Baseline placement uncertainty: ΔHF over the window width.
        double eBaseline = (baselineUncertaintyW * widthSafe) / massG;
        budget.add(new Transition.ErrorSource("ERR_BASELINE",
                "切线/基线放置不确定度 ΔHF×ΔT/m", eBaseline, baseline.id()));

        // 4. Temperature calibration uncertainty shifts window edges;
        //    contribution ≈ peak deviation × δT / m.
        double peakDev = 0;
        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            double t = p.tK();
            if (t < baseline.leftTK() || t > baseline.rightTK()) continue;
            Double meet = EventDetection.meetOf(baseline);
            double dev = (reversePolarity ? -1 : 1) * p.hfW()
                    - Integrator.baselinePiecewise(baseline, t, meet);
            peakDev = Math.max(peakDev, Math.abs(dev));
        }
        double eCal = (peakDev * calOffsetUncertaintyK) / massG;
        budget.add(new Transition.ErrorSource("ERR_CALIBRATION",
                "温度标定偏移不确定度 δT×峰高/m", eCal, null));

        // 5. Dropped/unconvertible points inside the window.
        double meanSpacing = widthSafe / Math.max(raw.pointsUsed() - 1, 1);
        double eGap = (peakDev * meanSpacing * unparseableInside) / massG;
        budget.add(new Transition.ErrorSource("ERR_UNCONVERTIBLE",
                "窗口内无法转换的仪器点（保留原位置，未为绘图丢弃）", eGap, null));

        double total = 0;
        for (Transition.ErrorSource e : budget) total += e.contributionJg() * e.contributionJg();
        return new Result(enthalpy, Math.sqrt(total), budget);
    }

    public static String signRule() {
        return RuleVersions.R_ENTHALPY_SIGN;
    }

    public static String normRule() {
        return RuleVersions.R_MASS_NORMALIZE;
    }
}
