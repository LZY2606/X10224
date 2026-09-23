package dsc.rules;

import dsc.model.SamplePoint;

import java.util.ArrayList;
import java.util.List;

/**
 * Measured statistics of an instrument trace. Direction and polarity are
 * decided from these numbers (data + instrument metadata), never from a
 * file name.
 */
public record TraceStats(int parseable, int unparseable, double dT, double dHF,
                         boolean nonUniform, double maxStep, double minStep,
                         double tFirstK, double tLastK, double hfFirstW, double hfLastW,
                         List<String> diagnostics) {

    /** Heating if temperature climbs through the leg, cooling if it falls. */
    public boolean temperatureAscending() {
        return dT > 0;
    }

    /**
     * The instrument emits heat flow with the "exo-up" convention but this
     * trace was archived with the inverted polarity (detected by its own
     * header/flag); integration must flip the sign accordingly.
     */
    public static TraceStats analyse(List<SamplePoint> points, boolean declaredReversePolarity) {
        List<String> diags = new ArrayList<>();
        int parseable = 0;
        int unparseable = 0;
        for (SamplePoint p : points) {
            if (p.parseable()) parseable++;
            else {
                unparseable++;
                diags.add("unconvertible-point#" + p.index() + ": " + p.diagnostic());
            }
        }
        if (parseable < 2) {
            diags.add("too-few-parseable-points:" + parseable);
            return new TraceStats(parseable, unparseable, 0, 0, false, 0, 0,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, diags);
        }

        SamplePoint first = firstParseable(points);
        SamplePoint last = lastParseable(points);
        double dT = last.tK() - first.tK();
        double dHF = last.hfW() - first.hfW();

        double maxStep = Double.NEGATIVE_INFINITY;
        double minStep = Double.POSITIVE_INFINITY;
        SamplePoint prev = null;
        for (SamplePoint p : points) {
            if (!p.parseable()) continue;
            if (prev != null) {
                double step = Math.abs(p.tK() - prev.tK());
                if (step > 1e-12) {
                    maxStep = Math.max(maxStep, step);
                    minStep = Math.min(minStep, step);
                } else {
                    diags.add("duplicate-temperature#" + p.index() + ":"
                            + String.format("%.3fK", p.tK()));
                }
            }
            prev = p;
        }
        boolean nonUniform = maxStep > minStep * 1.35 + 1e-9;
        if (nonUniform) {
            diags.add(String.format(
                    "non-uniform-temperature-grid: min=%.3fK max=%.3fK", minStep, maxStep));
        }
        if (declaredReversePolarity) {
            diags.add("reverse-heat-flow-polarity declared by instrument header; "
                    + "sign flipped during integration");
        }
        return new TraceStats(parseable, unparseable, dT, dHF, nonUniform, maxStep, minStep,
                first.tK(), last.tK(), first.hfW(), last.hfW(), diags);
    }

    private static SamplePoint firstParseable(List<SamplePoint> points) {
        return points.stream().filter(SamplePoint::parseable).findFirst().orElseThrow();
    }

    private static SamplePoint lastParseable(List<SamplePoint> points) {
        SamplePoint p = null;
        for (SamplePoint q : points) if (q.parseable()) p = q;
        if (p == null) throw new IllegalStateException();
        return p;
    }
}
