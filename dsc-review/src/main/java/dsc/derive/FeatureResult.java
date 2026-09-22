package dsc.derive;

import dsc.core.Quantity;
import dsc.core.Units;
import dsc.events.Judgment;
import dsc.model.EnthalpySign;
import dsc.model.NodeStatus;
import dsc.model.TransitionKind;

import java.util.List;

/**
 * Onset / peak / endset plus enthalpy for one region under one baseline.
 * Quantities carry units; uncertainty is itemized by source rather than
 * collapsed into a single error bar.
 */
public record FeatureResult(
        String id,
        String regionId,
        String baselineId,
        TransitionKind kind,
        Quantity onset,
        Quantity peak,
        Quantity endset,
        Quantity deltaHTotal,
        Quantity deltaHSpecific,
        EnthalpySign sign,
        NodeStatus status,
        String massIdUsed,
        String calibrationIdUsed,
        String ruleVersion,
        List<ErrorContribution> errorBudget,
        List<Judgment> judgments,
        List<String> dependencyIds,
        long derivedAtSeq) {

    public record ErrorContribution(String source, double magnitude, String unit, String basis) {}

    public static Quantity celsius(double v) { return new Quantity(v, Units.C); }
    public static Quantity milliJoule(double v) { return new Quantity(v, Units.MJ); }
    public static Quantity joulePerGram(double v) { return new Quantity(v, Units.J_PER_G); }
}
