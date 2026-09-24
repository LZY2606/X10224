package io.dsc.engine;

import io.dsc.model.BaselineCandidate;
import io.dsc.model.Derivation;
import io.dsc.model.DiagnosticCodes;
import io.dsc.model.Interval;
import io.dsc.model.Segment;
import io.dsc.model.StableId;
import io.dsc.model.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces the set of LEGITIMATE baseline candidates for a derivable interval.
 * Multiple reasonable baselines are always returned side by side (parallel
 * baselines) with their differences preserved; the engine never picks one.
 */
public final class BaselineEngine {

    public static class Output {
        public final List<BaselineCandidate> candidates = new ArrayList<>();
        public final List<String> diagnosticCodes = new ArrayList<>();
    }

    public Output compute(Interval interval, Segment segment,
                          String tangentMethod, int versionNo, Derivation derivation) {
        Output out = new Output();
        List<Segment.Sample> points = new Window(interval.fromK(), interval.toK()).select(segment);
        if (points.size() < 4) {
            out.diagnosticCodes.add(DiagnosticCodes.EMPTY_INTERVAL);
            return out;
        }
        String caseId = interval.caseId();
        String slot = interval.slot();

        boolean glass = "glass-tangent".equals(tangentMethod);
        if (glass) {
            out.candidates.add(glassTangent(caseId, slot, points, versionNo, derivation));
        } else {
            // parallel baselines for peak events: global linear vs endpoint chord
            out.candidates.add(linear("linear", caseId, slot, points, versionNo, derivation));
            out.candidates.add(linear("chord", caseId, slot,
                    List.of(points.get(0), points.get(points.size() - 1)),
                    versionNo, derivation));
            if (out.candidates.size() > 1) {
                out.diagnosticCodes.add(DiagnosticCodes.PARALLEL_BASELINES);
            }
        }
        return out;
    }

    private BaselineCandidate linear(String method, String caseId, String slot,
                                     List<Segment.Sample> points, int versionNo,
                                     Derivation derivation) {
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            xs[i] = points.get(i).tK();
            ys[i] = points.get(i).hfW();
        }
        LinearFit fit = LinearFit.of(xs, ys);
        StableId id = StableId.of("bl", slot + ":" + method + ":v" + versionNo);
        return new BaselineCandidate(id, caseId, slot, method, versionNo,
                fit.slope(), fit.intercept(), 0, 0, Double.NaN, fit.rms(),
                BaselineCandidate.Status.CURRENT, false, null, List.of(), derivation);
    }

    private BaselineCandidate glassTangent(String caseId, String slot,
                                           List<Segment.Sample> points, int versionNo,
                                           Derivation derivation) {
        int n = points.size();
        int q1 = Math.max(2, n / 4);
        int q3 = Math.min(n - 2, 3 * n / 4);
        LinearFit pre = fitSublist(points, 0, q1);
        LinearFit post = fitSublist(points, q3, n);
        double joinK = (avgT(points, q1 - 3, q1 + 3) + avgT(points, q3 - 3, q3 + 3)) / 2.0;
        double rms = glassRms(points, pre, post, joinK);
        StableId id = StableId.of("bl", slot + ":glass-tangent:v" + versionNo);
        return new BaselineCandidate(id, caseId, slot, "glass-tangent", versionNo,
                pre.slope(), pre.intercept(), post.slope(), post.intercept(), joinK, rms,
                BaselineCandidate.Status.CURRENT, false, null, List.of(), derivation);
    }

    private LinearFit fitSublist(List<Segment.Sample> pts, int from, int to) {
        from = Math.max(0, from);
        to = Math.min(pts.size(), to);
        double[] xs = new double[to - from];
        double[] ys = new double[to - from];
        for (int i = from; i < to; i++) {
            xs[i - from] = pts.get(i).tK();
            ys[i - from] = pts.get(i).hfW();
        }
        return LinearFit.of(xs, ys);
    }

    private double avgT(List<Segment.Sample> pts, int from, int to) {
        from = Math.max(0, from);
        to = Math.min(pts.size(), to);
        double sum = 0;
        for (int i = from; i < to; i++) sum += pts.get(i).tK();
        return sum / (to - from);
    }

    private double glassRms(List<Segment.Sample> pts, LinearFit pre, LinearFit post, double joinK) {
        double sum = 0;
        int n = 0;
        for (Segment.Sample p : pts) {
            double b = p.tK() < joinK ? pre.predict(p.tK()) : post.predict(p.tK());
            double r = p.hfW() - b;
            sum += r * r;
            n++;
        }
        return Math.sqrt(sum / n);
    }

    static double toW(double hfMw) {
        return Unit.MW.convert(hfMw, Unit.W).orElse(hfMw / 1000.0);
    }
}
