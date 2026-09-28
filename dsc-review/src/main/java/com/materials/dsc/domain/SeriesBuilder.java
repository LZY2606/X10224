package com.materials.dsc.domain;

import com.materials.dsc.model.Point;
import com.materials.dsc.model.Qty;
import com.materials.dsc.model.Segment;
import com.materials.dsc.model.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the derived working series for a segment: applies the temperature
 * calibration and normalises the endo/exo sign convention.  Nothing here
 * guesses direction from a file name; programmed metadata and measured trend
 * are compared and a disagreement is kept as a diagnostic.
 */
public final class SeriesBuilder {
    public static final class Sample {
        public final double tCalC;
        public final double hfEndoUpMw;
        public final Double timeS;
        public final boolean hfMissing;

        Sample(double tCalC, double hfEndoUpMw, Double timeS, boolean hfMissing) {
            this.tCalC = tCalC;
            this.hfEndoUpMw = hfEndoUpMw;
            this.timeS = timeS;
            this.hfMissing = hfMissing;
        }
    }

    public static final class Series {
        public final List<Sample> acquisitionOrder = new ArrayList<>();
        public String direction;
        public String basis;
        public String diagnostic;
        public boolean reversedSign;
        public boolean nonUniform;
        public int skippedPoints;
        public double trendSlope;
    }

    private SeriesBuilder() {
    }

    public static Qty parseOffset(String raw) {
        if (raw == null || raw.isBlank()) {
            return Qty.parsed("0", 0.0, Units.CELSIUS);
        }
        return Units.parse(raw, Units.TEMPERATURE, "temperature calibration offset");
    }

    public static Series build(Segment seg, double calOffsetC) {
        Series s = new Series();
        s.reversedSign = seg.heatFlowReversed;
        double[] tArr = new double[seg.points.size()];
        int n = 0;
        for (Point p : seg.points) {
            double hf;
            boolean missing = p.hfMw == null;
            if (missing) {
                hf = Double.NaN;
                s.skippedPoints++;
            } else {
                hf = seg.heatFlowReversed ? -p.hfMw : p.hfMw;
            }
            s.acquisitionOrder.add(new Sample(p.tempC + calOffsetC, hf, p.timeS, missing));
            tArr[n++] = p.tempC;
        }
        // Temperature trend over acquisition order (linear least squares).
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int m = 0;
        int signChanges = 0;
        double prevStep = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = tArr[i];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
            m++;
            if (i > 0) {
                double step = y - tArr[i - 1];
                if (i > 1 && Math.signum(step) != Math.signum(prevStep)
                        && Math.abs(step) > 1e-9 && Math.abs(prevStep) > 1e-9) {
                    signChanges++;
                }
                prevStep = step;
            }
        }
        double denom = m * sxx - sx * sx;
        double slope = denom == 0 ? 0 : (m * sxy - sx * sy) / denom;
        s.trendSlope = slope;
        String inferred = slope > 1e-6 ? "heating" : slope < -1e-6 ? "cooling" : "ambiguous";
        String programmed = seg.directionProgrammed == null ? ""
                : seg.directionProgrammed.trim().toLowerCase();
        if (programmed.equals("heating") || programmed.equals("cooling")) {
            s.direction = programmed;
            s.basis = "programmed";
            if (!inferred.equals("ambiguous") && !inferred.equals(programmed)) {
                s.diagnostic = "programmed direction '" + programmed
                        + "' disagrees with measured temperature trend '" + inferred
                        + "'; kept programmed, segment flagged for review";
            }
        } else {
            s.direction = inferred;
            s.basis = "temperature-trend";
            if (inferred.equals("ambiguous")) {
                s.diagnostic = "no programmed direction and temperature trend is flat/non-monotonic; "
                        + "direction cannot be decided from the file name";
            }
        }
        if (signChanges > 0) {
            String extra = "temperature sequence changes direction " + signChanges
                    + " time(s); possible cycle switch inside segment";
            s.diagnostic = s.diagnostic == null ? extra : s.diagnostic + "; " + extra;
        }
        // Non-uniform spacing in the acquisition temperature coordinate.
        double[] gaps = new double[Math.max(0, n - 1)];
        double sum = 0;
        for (int i = 1; i < n; i++) {
            gaps[i - 1] = Math.abs(tArr[i] - tArr[i - 1]);
            sum += gaps[i - 1];
        }
        if (gaps.length > 1) {
            double mean = sum / gaps.length;
            for (double g : gaps) {
                if (mean > 0 && (g > mean * 1.6 || g < mean * 0.4)) {
                    s.nonUniform = true;
                    break;
                }
            }
        }
        return s;
    }
}
