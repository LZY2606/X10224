package org.materials.dsc.engine;

import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.DisplayValue;
import org.materials.dsc.domain.EnthalpyResult;
import org.materials.dsc.domain.ErrorComponent;
import org.materials.dsc.domain.MassRecord;
import org.materials.dsc.domain.Segment;
import org.materials.dsc.domain.ThermalSign;
import org.materials.dsc.domain.Transition;
import org.materials.dsc.domain.TransitionKind;
import org.materials.dsc.domain.Units;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 积分：仅在单个扫描段、选定基线上做非等距梯形积分。
 * 面积 mW·°C ÷ (°C/min) × (1 min/60 s) = mJ。
 * 归一化 J/g 依赖当前质量版本；质量修订后该派生标记过期，不能拿旧值冒充新值。
 */
public final class Integrator {
    private Integrator() {
    }

    public static final double MASS_UNCERTAINTY_FRAC = 0.001;
    public static final double RATE_UNCERTAINTY_FRAC = 0.01;
    public static final double CAL_UNCERTAINTY_C = 0.2;

    public static EnthalpyResult integrate(Segment seg, Transition tr, BaselineCandidate bl,
                                           Normalizer.Series s, MassRecord mass, double rateCelsiusPerMin,
                                           double nonUniformRelStep, String ruleVersion, String eventSeq) {
        int lo = tr.loIndex();
        int hi = tr.hiIndex();
        double[] residual = new double[s.length()];
        for (int i = lo; i <= hi; i++) {
            residual[i] = s.valid[i] ? s.hfMw[i] - bl.valueAtCelsius(s.tCelsius[i]) : 0;
        }
        double area = EngineMath.trapezoidCelsius(s.tCelsius, residual, lo, hi);
        double signedAreaMilliJoule = area * 60.0 / rateCelsiusPerMin;
        if (seg.cooling()) {
            signedAreaMilliJoule = -signedAreaMilliJoule;
        }
        ThermalSign sign = signedAreaMilliJoule >= 0 ? ThermalSign.EXOTHERMIC : ThermalSign.ENDOTHERMIC;
        double enthalpyJ = signedAreaMilliJoule / 1000.0;

        String baseId = tr.id() + "-" + bl.id();
        List<ErrorComponent> errors = new ArrayList<>();
        errors.add(new ErrorComponent("baseline", "基线选择不确定性",
                Math.abs(enthalpyJ) * Math.max(0.01, (1 - bl.quality()) * 0.15), "J", bl.id()));
        errors.add(new ErrorComponent("rate", "扫描速率不确定度",
                Math.abs(enthalpyJ) * RATE_UNCERTAINTY_FRAC, "J", seg.id()));
        errors.add(new ErrorComponent("nonuniform-grid", "非等间隔温度网格",
                Math.abs(enthalpyJ) * Math.min(0.05, nonUniformRelStep * 0.1), "J", bl.transitionId()));
        double enthalpyUncertainty = errors.stream().mapToDouble(ErrorComponent::contribution)
                .map(x -> x * x).sum();
        enthalpyUncertainty = Math.sqrt(enthalpyUncertainty);

        DisplayValue enthalpyDv = new DisplayValue(baseId + "-enthalpy", "enthalpy",
                enthalpyJ, "J", enthalpyUncertainty, errors,
                List.of(bl.id(), seg.id()), bl.id(), ruleVersion, eventSeq, false);

        DisplayValue specific = null;
        if (mass != null && mass.mass().convertible()) {
            double grams = mass.mass().canonical();
            double jg = Units.JOULE.toCanonical(enthalpyJ) / grams;
            double massErr = Math.abs(enthalpyJ) / grams * MASS_UNCERTAINTY_FRAC;
            List<ErrorComponent> specErrors = new ArrayList<>(errors);
            specErrors.add(new ErrorComponent("mass", "样品质量不确定度",
                    massErr, "J/g", mass.id()));
            double u = specErrors.stream().mapToDouble(ErrorComponent::contribution)
                    .map(x -> x * x).sum();
            specific = new DisplayValue(baseId + "-specific-enthalpy", "specific-enthalpy",
                    jg, "J/g", Math.sqrt(u), specErrors,
                    List.of(baseId + "-enthalpy", mass.id()), baseId + "-enthalpy",
                    ruleVersion, eventSeq, false);
        }

        return new EnthalpyResult(baseId + "-analysis", tr.id(), bl.id(),
                seg.traceId(), seg.id(), tr.resolvedKind() == TransitionKind.GLASS
                        ? tr.thermalSign() : sign,
                enthalpyDv, specific, mass == null ? null : mass.id(),
                area, rateCelsiusPerMin, errors, ruleVersion, eventSeq, false, null);
    }

    static String fmt(double v) {
        return String.format(Locale.ROOT, "%.6g", v);
    }
}
