package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalized curve: canonical endo-up sign, heat flow per unit mass.
 * Every normalization records exactly which mass version and which sign
 * convention it used, so dependency propagation can stale it later.
 * Points are carried on their original (possibly non-uniform) temperature
 * grid; nothing is resampled for plotting convenience.
 */
public final class Curve {
    public final String segmentRef;        // provenance: segment id
    public final Segments.Kind kind;
    public final String massVersionRef;    // provenance: mass version id
    public final String calibrationRef;    // provenance: calibration id
    public final double[] tempC;           // coordinate basis: degC, calibration applied
    public final double[] heatMwPerMg;     // mW/mg == W/g, canonical endo-up
    public final List<String> diagnostics = new ArrayList<>();

    public Curve(String segmentRef, Segments.Kind kind, String massVersionRef,
                 String calibrationRef, double[] tempC, double[] heatMwPerMg) {
        this.segmentRef = segmentRef;
        this.kind = kind;
        this.massVersionRef = massVersionRef;
        this.calibrationRef = calibrationRef;
        this.tempC = tempC;
        this.heatMwPerMg = heatMwPerMg;
    }

    public int size() {
        return tempC.length;
    }

    /**
     * Builds a normalized curve from raw points. Returns null when the sign
     * convention is undetermined: normalization refuses to guess direction.
     */
    public static Curve normalize(String segmentRef, Segments.Kind kind,
                                  InstrumentFile file, Segments.Segment seg,
                                  double massMg, String massVersionRef,
                                  double tempOffsetC, String calibrationRef) {
        InstrumentFile.SignConvention sc = file.signConvention();
        if (sc == InstrumentFile.SignConvention.UNDETERMINED) {
            return null;
        }
        double sign = sc == InstrumentFile.SignConvention.EXO_UP ? -1.0 : 1.0;
        List<Double> ts = new ArrayList<>();
        List<Double> hs = new ArrayList<>();
        for (int i = seg.fromIndex; i <= seg.toIndex; i++) {
            InstrumentFile.Point p = file.points.get(i);
            if (!p.usable()) continue; // unconvertible values stay in the raw layer
            ts.add(p.tempC + tempOffsetC);
            hs.add(sign * p.heatMw / massMg);
        }
        double[] t = ts.stream().mapToDouble(Double::doubleValue).toArray();
        double[] h = hs.stream().mapToDouble(Double::doubleValue).toArray();
        return new Curve(segmentRef, kind, massVersionRef, calibrationRef, t, h);
    }
}
