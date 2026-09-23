package dsc;

import dsc.domain.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {

    private static Curve.Pt p(double t, double temp, double hf) {
        return new Curve.Pt(t, temp, hf, t + "," + temp + "," + hf);
    }

    @Test
    void cycleSegmentSwitching_neverMixesHeatingAndCooling() {
        List<Curve.Pt> pts = new ArrayList<>();
        double t = 0;
        for (double T = 25; T <= 100; T += 5) pts.add(p(t += 30, T, 1.0));   // heating
        for (double T = 95; T >= 30; T -= 5) pts.add(p(t += 30, T, 1.2));   // cooling
        for (double T = 35; T <= 80; T += 5) pts.add(p(t += 30, T, 1.1));   // heating again
        Curve c = new Curve(pts, "mW", "degC", "EXO_UP");
        List<Curve.Segment> segs = c.splitSegments();
        assertEquals(3, segs.size());
        assertEquals(Curve.Direction.HEATING, segs.get(0).direction);
        assertEquals(Curve.Direction.COOLING, segs.get(1).direction);
        assertEquals(Curve.Direction.HEATING, segs.get(2).direction);
        // every segment is single-direction: no mixing
        for (Curve.Segment s : segs) {
            for (int i = 1; i < s.points.size(); i++) {
                double d = s.points.get(i).tempC - s.points.get(i - 1).tempC;
                if (s.direction == Curve.Direction.HEATING) assertTrue(d >= -1e-9);
                if (s.direction == Curve.Direction.COOLING) assertTrue(d <= 1e-9);
            }
        }
    }

    @Test
    void reverseHeatFlow_isDetected() {
        List<Curve.Pt> pts = new ArrayList<>();
        for (int i = 0; i < 50; i++) pts.add(p(i, 25 + i, 1.0));
        pts.get(25); pts.set(25, p(25, 50, -0.8)); // sign reversal mid-segment
        Curve c = new Curve(pts, "mW", "degC", "EXO_UP");
        Curve.Segment s = c.splitSegments().get(0);
        assertTrue(Curve.hasReverseHeatFlow(s));
    }

    @Test
    void directionUnknown_whenNoConventionMetadata_neverGuessed() {
        List<Curve.Pt> pts = new ArrayList<>();
        for (int i = 0; i < 40; i++) pts.add(p(i, 100 + i, 1.0 + (i == 20 ? 3 : 0)));
        Curve c = new Curve(pts, "mW", "degC", null); // no metadata
        Curve.Segment s = c.splitSegments().get(0);
        Baseline b = Baseline.candidates("ev", s.points, 105, 135).get(0);
        Integrator.Result r = Integrator.integrate(s, b, 10.0, 10.0, c.exoConvention);
        assertTrue(r.diagnostics.stream().anyMatch(d -> d.code.equals("DIRECTION_UNKNOWN")));
    }

    @Test
    void nonUniformSpacing_integratesOnActualTemperatures() {
        // triangle peak on irregular grid; compare against dense reference
        List<Curve.Pt> sparse = new ArrayList<>();
        double[] temps = {100, 103, 108, 109.5, 115, 121, 130};
        for (double T : temps) sparse.add(p(T, T, T <= 115 ? (T - 100) / 15.0 : (130 - T) / 15.0));
        Curve.Segment seg = new Curve.Segment(Curve.Direction.HEATING, sparse);
        assertFalse(seg.isUniformSpacing());
        Baseline b = Baseline.candidates("ev", sparse, 100, 130).stream()
                .filter(x -> x.kind == Baseline.Kind.LINEAR).findFirst().orElseThrow();
        Integrator.Result r = Integrator.integrate(seg, b, 10.0, 10.0, "EXO_UP");
        assertNotNull(r.enthalpyJg);
        assertTrue(r.errorSources.stream().anyMatch(e -> e.contains("non-uniform")));
        // triangle area: 0.5 * 30K * 1.0 mW = 15 mW*K; dt = dT/(rate/60) -> energy = area*60/10 = 90 mJ; /10mg = 9 J/g
        assertEquals(9.0, r.enthalpyJg, 0.6);
    }

    @Test
    void overlappingTransitions_keepParallelBaselineCandidates() {
        List<Curve.Pt> pts = new ArrayList<>();
        for (int i = 0; i <= 200; i++) {
            double T = 200 + i * 0.25;
            double hf = 0.5
                    + 3.0 * Math.exp(-Math.pow((T - 220) / 4.0, 2))
                    + 1.2 * Math.exp(-Math.pow((T - 231) / 2.5, 2));
            pts.add(p(T, T, hf));
        }
        Curve.Segment seg = new Curve.Segment(Curve.Direction.HEATING, pts);
        List<Baseline> cands = Baseline.candidates("ev-melt", pts, 205, 245);
        assertEquals(3, cands.size()); // LINEAR + TANGENT + SIGMOIDAL all retained
        Integrator.Result r0 = Integrator.integrate(seg, cands.get(0), 10, 10, "EXO_UP");
        Integrator.Result r2 = Integrator.integrate(seg, cands.get(2), 10, 10, "EXO_UP");
        assertNotEquals(r0.enthalpyJg, r2.enthalpyJg); // differences preserved, not collapsed
    }

    @Test
    void emptyInterval_yieldsDiagnosticNotNumber() {
        List<Curve.Pt> pts = new ArrayList<>();
        for (int i = 0; i < 50; i++) pts.add(p(i, 25 + i, 1.0));
        Curve.Segment seg = new Curve.Segment(Curve.Direction.HEATING, pts);
        List<Baseline> cands = Baseline.candidates("ev-empty", pts, 400, 450);
        assertTrue(cands.isEmpty()); // no baseline fabricated for empty interval
        Baseline fake = Baseline.candidates("x", pts, 25, 74).get(0);
        Baseline emptyRange = new Baseline("bl-ev-empty-LINEAR", Baseline.Kind.LINEAR, 400, 450, new double[]{1, 1});
        Integrator.Result r = Integrator.integrate(seg, emptyRange, 10, 10, "EXO_UP");
        assertNull(r.enthalpyJg);
        assertTrue(r.diagnostics.stream().anyMatch(d -> d.code.equals("EMPTY_INTERVAL")));
        assertNotNull(fake);
    }
}
