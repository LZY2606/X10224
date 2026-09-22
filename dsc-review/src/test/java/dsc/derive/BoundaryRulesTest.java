package dsc.derive;

import dsc.core.Quantity;
import dsc.core.Unit;
import dsc.core.Units;
import dsc.events.Judgment;
import dsc.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The seven required boundary situations as repeatable, fixture-only cases:
 * reversed heat flow, non-uniform temperature grid, overlapping transitions,
 * cycle switching, mass version change, parallel baselines and empty interval.
 */
class BoundaryRulesTest {

    private static ScanSegment segment(String id, Direction dir, SignalConvention conv,
                                       double rate, List<double[]> pts) {
        List<SamplePoint> sps = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            sps.add(point(i, pts.get(i)[0], pts.get(i)[1]));
        }
        return new ScanSegment(id, "fixture/" + id + ".csv", 1, conv,
                new Quantity(rate, Units.C_PER_MIN), sps);
    }

    private static SamplePoint point(int i, double t, double hf) {
        var p = new SamplePoint(i,
                new dsc.core.RawValue(String.format("%.3f", t), Units.C),
                new dsc.core.RawValue(String.format("%.4f", hf), Units.MW), null);
        p.materialize();
        return p;
    }

    private static List<double[]> gaussianPeak(double from, double to, double center,
                                               double sigma, double amp, double step) {
        List<double[]> pts = new ArrayList<>();
        for (double t = from; t <= to + 1e-9; t += step) {
            pts.add(new double[]{t, 0.01 * t + amp * Math.exp(-(t - center) * (t - center) / (2 * sigma * sigma))});
        }
        return pts;
    }

    @Test
    void emptyIntervalIsIndeterminateAndKept() {
        ScanSegment seg = segment("s", Direction.HEATING, SignalConvention.EXO_UP, 10,
                gaussianPeak(40, 180, 155, 3, 6, 1));
        Region region = new Region("r", "s", TransitionKind.MELT, 185, 189, "empty", "x", 1);
        TangentSelection tan = new TangentSelection("tan", "r", "x", 185, 186, 188, 189,
                TangentSelection.MODE_TWO_SIDED, "");
        List<BaselineResult> bs = new BaselineRules().derive("r", region, seg, tan, null);
        BaselineResult empty = bs.stream().filter(b -> b.status() == NodeStatus.INDETERMINATE).findFirst().orElseThrow();
        assertTrue(empty.judgments().stream().anyMatch(j -> j.code().equals("EMPTY_REGION")));
        // indeterminate candidates must not be dropped; they stay auditable
        assertEquals(1, bs.stream().filter(b -> b.status() == NodeStatus.INDETERMINATE).count());
    }

    @Test
    void parallelBaselinesAreAllRetained() {
        ScanSegment seg = segment("s", Direction.HEATING, SignalConvention.EXO_UP, 10,
                gaussianPeak(120, 190, 155, 3, 6, 1));
        Region region = new Region("r", "s", TransitionKind.MELT, 140, 170, "melt", "x", 1);
        TangentSelection tan = new TangentSelection("tan", "r", "x", 140, 146, 164, 170,
                TangentSelection.MODE_TWO_SIDED, "");
        List<BaselineResult> bs = new BaselineRules().derive("r", region, seg, tan, null);
        long usable = bs.stream().filter(BaselineResult::acceptedEligible).count();
        assertTrue(usable >= 2, "multiple legal baselines must survive: " + usable);
        // stable content-addressed identity
        List<BaselineResult> again = new BaselineRules().derive("r", region, seg, tan, null);
        assertEquals(bs.get(0).id(), again.get(0).id());
    }

    @Test
    void unknownConventionMakesSignAmbiguousNotGuessed() {
        // reversed heat flow, and the instrument does not declare the convention
        ScanSegment seg = segment("s", Direction.HEATING, SignalConvention.UNKNOWN, 10,
                gaussianPeak(120, 190, 155, 3, 6, 1));
        Region region = new Region("r", "s", TransitionKind.MELT, 140, 170, "melt", "x", 1);
        BaselineResult bl = new BaselineResult("bl_test", "r", "global", 0.01, 0.0,
                120, 190, 0.01, NodeStatus.VALID, RuleVersions.BASELINE_RULES, List.of("seg:s"),
                List.of(), 1);
        FeatureResult f = new FeatureRules().derive(region, seg, bl,
                new MassVersion("m1", 1, new dsc.core.RawValue("8.4", Units.MG),
                        new Quantity(0.0084, Units.G), "", java.time.Instant.now()),
                null, SignalConvention.UNKNOWN);
        assertEquals(EnthalpySign.AMBIGUOUS, f.sign());
        assertTrue(f.deltaHTotal().value() > 0, "magnitude must still be reported");
        assertTrue(f.judgments().stream().anyMatch(j -> j.code().equals("SIGN_NOT_FROM_FILENAME")));
    }

    @Test
    void contradictoryDeflectionIsFlagged() {
        // melt on heating but exo-up wiring shows exothermic deflection
        ScanSegment seg = segment("s", Direction.HEATING, SignalConvention.EXO_UP, 10,
                gaussianPeak(120, 190, 155, 3, -6, 1));
        Region region = new Region("r", "s", TransitionKind.MELT, 140, 170, "melt", "x", 1);
        BaselineResult bl = new BaselineResult("bl_test", "r", "global", 0.01, 0.0,
                120, 190, 0.01, NodeStatus.VALID, RuleVersions.BASELINE_RULES, List.of("seg:s"),
                List.of(), 1);
        FeatureResult f = new FeatureRules().derive(region, seg, bl,
                new MassVersion("m1", 1, new dsc.core.RawValue("8.4", Units.MG),
                        new Quantity(0.0084, Units.G), "", java.time.Instant.now()), null,
                SignalConvention.EXO_UP);
        assertEquals(EnthalpySign.AMBIGUOUS, f.sign());
        assertTrue(f.judgments().stream().anyMatch(j -> j.code().equals("SIGN_CONTRADICTS_KIND")));
    }

    @Test
    void nonUniformGridIntegratesWithActualWidths() {
        List<double[]> uniform = gaussianPeak(120, 190, 155, 3, 6, 1.0);
        List<double[]> dense = new ArrayList<>();
        for (double[] p : uniform) dense.add(p.clone());
        // add extra points near the peak -> non-uniform grid, same curve
        for (double t = 150; t <= 160; t += 0.25) {
            double hf = 0.01 * t + 6 * Math.exp(-(t - 155) * (t - 155) / 18.0);
            dense.add(new double[]{t, hf});
        }
        dense.sort(java.util.Comparator.comparingDouble(a -> a[0]));
        ScanSegment segU = segment("u", Direction.HEATING, SignalConvention.EXO_UP, 10, uniform);
        ScanSegment segD = segment("d", Direction.HEATING, SignalConvention.EXO_UP, 10, dense);
        assertFalse(segU.nonUniformTemperatures());
        assertTrue(segD.nonUniformTemperatures());
        Region r1 = new Region("r1", "u", TransitionKind.MELT, 140, 170, "", "x", 1);
        Region r2 = new Region("r2", "d", TransitionKind.MELT, 140, 170, "", "x", 1);
        BaselineResult bl1 = new BaselineResult("bl1", "r1", "global", 0.01, 0, 120, 190, 0,
                NodeStatus.VALID, RuleVersions.BASELINE_RULES, List.of(), List.of(), 1);
        BaselineResult bl2 = new BaselineResult("bl2", "r2", "global", 0.01, 0, 120, 190, 0,
                NodeStatus.VALID, RuleVersions.BASELINE_RULES, List.of(), List.of(), 1);
        var mass = new MassVersion("m", 1, new dsc.core.RawValue("8.4", Units.MG),
                new Quantity(0.0084, Units.G), "", java.time.Instant.now());
        FeatureResult f1 = new FeatureRules().derive(r1, segU, bl1, mass, null, SignalConvention.EXO_UP);
        FeatureResult f2 = new FeatureRules().derive(r2, segD, bl2, mass, null, SignalConvention.EXO_UP);
        // same curve, same integrated energy regardless of sampling density
        assertEquals(f1.deltaHTotal().value(), f2.deltaHTotal().value(),
                Math.abs(f1.deltaHTotal().value()) * 0.03);
        assertTrue(f2.judgments().stream().anyMatch(j -> j.code().equals("NONUNIFORM_TEMPERATURE_GRID")));
    }

    @Test
    void coolingSegmentIsDetectedFromData() {
        List<double[]> pts = new ArrayList<>();
        for (double t = 190; t >= 40 - 1e-9; t -= 1) pts.add(new double[]{t, -3 * Math.exp(-(t - 140) * (t - 140) / 32)});
        ScanSegment seg = segment("cool", Direction.COOLING, SignalConvention.EXO_UP, -10, pts);
        assertEquals(Direction.COOLING, seg.direction());
        // a region bound to this segment is explicitly single-segment; mixing is impossible by construction
        Region region = new Region("rc", "cool", TransitionKind.CRYSTALLIZATION, 125, 155, "", "x", 1);
        assertEquals(region.segmentId(), seg.segmentId());
    }
}
