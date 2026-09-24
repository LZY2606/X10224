package org.materials.dsc.rules;

import org.materials.dsc.domain.BaselineCandidate;
import org.materials.dsc.domain.Ids;
import org.materials.dsc.domain.Interval;
import org.materials.dsc.domain.NormalizedCurve;
import org.materials.dsc.domain.SourceDocs;
import org.materials.dsc.domain.ThermalEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces *all* legally supported baselines for an event.
 *
 * Two legitimate constructions are kept side by side whenever both are
 * available:
 *   1. two-tangent straight line from the archived user tangent anchors
 *      (pre/post each need at least one usable anchor; lines are fit by least
 *      squares when an arm has multiple anchors);
 *   2. straight chord from the first/last usable point of the window, used as
 *      the independent automatic reference ("window_chord").
 *
 * When tangent anchors are degenerate they are diagnosed, not silently
 * replaced by another choice; the window chord remains a valid candidate.
 */
public final class BaselineEngine {

    public static final String METHOD_TANGENT = "two_tangent";
    public static final String METHOD_CHORD = "window_chord";

    public static final class Build {
        public final List<BaselineCandidate> candidates = new ArrayList<>();
        public final List<String> diagnostics = new ArrayList<>();
    }

    public Build build(ThermalEvent event, NormalizedCurve curve,
                       SourceDocs.TangentSelection tangent) {
        Build out = new Build();
        Interval w = event.window;

        BaselineCandidate chord = chordCandidate(event, curve);
        if (chord != null) {
            out.candidates.add(chord);
        } else {
            out.diagnostics.add("WINDOW_HAS_NO_USABLE_POINTS: " + w.startC + ".." + w.endC);
        }

        BaselineCandidate tan = tangentCandidate(event, curve, tangent, out.diagnostics);
        if (tan != null) {
            out.candidates.add(tan);
        }

        for (BaselineCandidate c : out.candidates) {
            c.inputs.add(event.segmentId + "/" + SourceDocs.INSTRUMENT_OUTPUT);
        }
        return out;
    }

    private BaselineCandidate chordCandidate(ThermalEvent event, NormalizedCurve curve) {
        int first = -1;
        int last = -1;
        for (int i = 0; i < curve.tempC.length; i++) {
            if (!curve.usable[i]) {
                continue;
            }
            if (event.window.contains(curve.tempC[i])) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            }
        }
        if (first < 0 || first == last) {
            return null;
        }
        double[] line = lineThrough(curve, first, last);
        BaselineCandidate c = base(Ids.baseline(event.id, METHOD_CHORD), event, METHOD_CHORD,
                "Window chord (first-last usable point)");
        c.slope = line[0];
        c.intercept = line[1];
        c.fitRmsResidualW = rms(curve, first, last, line);
        c.preAnchorCount = 1;
        c.postAnchorCount = 1;
        return c;
    }

    private BaselineCandidate tangentCandidate(ThermalEvent event, NormalizedCurve curve,
                                               SourceDocs.TangentSelection tangent,
                                               List<String> diagnostics) {
        if (tangent == null) {
            diagnostics.add("NO_TANGENT_SELECTION: only automatic baselines available for " + event.id);
            return null;
        }
        double[][] pre = usableAnchorXY(tangent.preAnchors, curve, diagnostics, "pre");
        double[][] post = usableAnchorXY(tangent.postAnchors, curve, diagnostics, "post");
        if (pre.length < 1 || post.length < 1) {
            diagnostics.add("DEGENERATE_TANGENT: need >=1 usable anchor on each arm for "
                    + tangent.id + " (pre=" + pre.length + ", post=" + post.length + ")");
            return null;
        }
        double[] preLine = fitLine(pre);
        double[] postLine = fitLine(post);

        // Two-tangent baseline: linear ramp connecting the two arm lines at the
        // window boundaries.  Represented exactly as slope/intercept between
        // window start and end so integration is a single closed form.
        double yAtStart = preLine[0] * event.window.startC + preLine[1];
        double yAtEnd = postLine[0] * event.window.endC + postLine[1];
        double dx = event.window.endC - event.window.startC;
        BaselineCandidate c = base(Ids.baseline(event.id, METHOD_TANGENT), event, METHOD_TANGENT,
                "User two-tangent (archived anchors)");
        if (dx == 0.0) {
            diagnostics.add("EMPTY_INTERVAL: tangent baseline cannot ramp across zero-width window "
                    + event.id);
            return null;
        }
        c.slope = (yAtEnd - yAtStart) / dx;
        c.intercept = yAtStart - c.slope * event.window.startC;
        c.preAnchorCount = pre.length;
        c.postAnchorCount = post.length;
        c.sourceTangentId = tangent.id;
        double ss = 0;
        int count = 0;
        for (double[] p : pre) {
            double r = preLine[0] * p[0] + preLine[1] - p[1];
            ss += r * r;
            count++;
        }
        for (double[] p : post) {
            double r = postLine[0] * p[0] + postLine[1] - p[1];
            ss += r * r;
            count++;
        }
        c.fitRmsResidualW = count == 0 ? 0 : Math.sqrt(ss / count);
        c.inputs.add(tangent.id);
        return c;
    }

    private double[][] usableAnchorXY(List<SourceDocs.TangentAnchor> anchors, NormalizedCurve curve,
                                      List<String> diagnostics, String arm) {
        List<double[]> out = new ArrayList<>();
        for (SourceDocs.TangentAnchor a : anchors) {
            if (a.pointIndex < 0 || a.pointIndex >= curve.tempC.length || !curve.usable[a.pointIndex]) {
                diagnostics.add("TANGENT_ANCHOR_UNUSABLE: " + arm + " pointIndex=" + a.pointIndex
                        + " (unconvertible or out of range), kept in archive, ignored by fit");
                continue;
            }
            out.add(new double[] { curve.tempC[a.pointIndex], curve.heatW[a.pointIndex] });
        }
        return out.toArray(new double[0][]);
    }

    private BaselineCandidate base(String id, ThermalEvent event, String method, String label) {
        BaselineCandidate c = new BaselineCandidate();
        c.id = id;
        c.eventId = event.id;
        c.method = method;
        c.label = label;
        return c;
    }

    private double[] lineThrough(NormalizedCurve curve, int i, int j) {
        double dx = curve.tempC[j] - curve.tempC[i];
        double m = dx == 0 ? 0 : (curve.heatW[j] - curve.heatW[i]) / dx;
        return new double[] { m, curve.heatW[i] - m * curve.tempC[i] };
    }

    private double[] fitLine(double[][] pts) {
        if (pts.length == 1) {
            return new double[] { 0, pts[0][1] };
        }
        int n = pts.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : pts) {
            sx += p[0];
            sy += p[1];
            sxx += p[0] * p[0];
            sxy += p[0] * p[1];
        }
        double den = n * sxx - sx * sx;
        if (den == 0) {
            return new double[] { 0, sy / n };
        }
        double m = (n * sxy - sx * sy) / den;
        return new double[] { m, (sy - m * sx) / n };
    }

    private double rms(NormalizedCurve curve, int first, int last, double[] line) {
        double ss = 0;
        int n = 0;
        for (int i = first; i <= last; i++) {
            if (!curve.usable[i]) {
                continue;
            }
            double r = line[0] * curve.tempC[i] + line[1] - curve.heatW[i];
            ss += r * r;
            n++;
        }
        return n == 0 ? 0 : Math.sqrt(ss / n);
    }
}
