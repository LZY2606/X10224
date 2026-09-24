package dsc.engine;

import dsc.model.Quant;
import dsc.model.RawPoint;
import dsc.model.SegmentDef;
import dsc.model.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived-layer conversion. Preserves every source token in the archived segment; only this
 * layer interprets units, calibration offset, mass normalisation and sign convention.
 *
 * <p>Key semantics:</p>
 * <ul>
 *   <li>endothermic-up is the canonical y frame. The instrument's declared convention decides the
 *       flip ({@code EXO_UP} must be inverted). File names are never consulted.</li>
 *   <li>cooling segments keep their own temperature ordering; heat and cool legs are normalised
 *       independently and never joined.</li>
 *   <li>a value that cannot be converted stays at its source position (index) with a diagnostic
 *       and is skipped rather than dropped from the picture.</li>
 * </ul>
 */
public final class Normalizer {

    private Normalizer() {
    }

    public record Normalized(Series series, boolean signWasReversed,
                             List<ConversionIssue> issues) {
    }

    public record ConversionIssue(String code, int rowIndex, String detail) {
    }

    public static Normalized normalize(SegmentDef segment, Double massMg, Double calibrationOffsetC) {
        List<ConversionIssue> issues = new ArrayList<>();
        List<RawPoint> rows = segment.points();
        double[] tOrig = new double[rows.size()];
        double[] t = new double[rows.size()];
        double[] y = new double[rows.size()];
        List<Integer> valid = new ArrayList<>();
        List<Integer> unconverted = new ArrayList<>();

        boolean conventionKnown = "EXO_UP".equalsIgnoreCase(segment.hfConvention())
                || "ENDO_UP".equalsIgnoreCase(segment.hfConvention());
        if (!conventionKnown) {
            issues.add(new ConversionIssue("DIRECTION_AMBIGUOUS", -1,
                    "segment " + segment.segmentId() + " declares no EXO_UP/ENDO_UP convention"));
        }

        boolean ascending = true;
        double prevT = Double.NaN;
        double minGap = Double.POSITIVE_INFINITY;
        double maxGap = 0;

        for (RawPoint rp : rows) {
            Double tv;
            Double yv;
            try {
                tv = Double.parseDouble(rp.tempToken());
            } catch (NumberFormatException ex) {
                tv = null;
            }
            try {
                yv = Double.parseDouble(rp.hfToken());
            } catch (NumberFormatException ex) {
                yv = null;
            }
            int idx = rp.rowIndex();
            if (tv == null) {
                issues.add(new ConversionIssue("UNCONVERTED_VALUE", idx,
                        "temperature token '" + rp.tempToken() + "' not numeric; kept at row " + idx));
                unconverted.add(idx);
                continue;
            }
            Double tc = Units.convertTemperature(tv, rp.tempUnit(), false);
            if (tc == null) {
                issues.add(new ConversionIssue("UNCONVERTED_VALUE", idx,
                        "unknown temperature unit '" + rp.tempUnit() + "' at row " + idx));
                unconverted.add(idx);
                continue;
            }
            if (calibrationOffsetC != null) {
                tc += calibrationOffsetC;
            }
            if (yv == null) {
                issues.add(new ConversionIssue("UNCONVERTED_VALUE", idx,
                        "heat-flow token '" + rp.hfToken() + "' not numeric; kept at row " + idx));
                unconverted.add(idx);
                tOrig[idx] = tv;
                t[idx] = tc;
                continue;
            }
            Double ywg = Units.convertHeatFlow(yv, rp.hfUnit(), massMg);
            if (ywg == null) {
                issues.add(new ConversionIssue("UNCONVERTED_VALUE", idx,
                        "cannot convert heat flow '" + rp.hfToken() + " " + rp.hfUnit()
                                + "' without usable mass at row " + idx));
                unconverted.add(idx);
                tOrig[idx] = tv;
                t[idx] = tc;
                continue;
            }
            tOrig[idx] = tv;
            t[idx] = tc;
            y[idx] = ywg;
            valid.add(idx);

            if (!Double.isNaN(prevT) && tc <= prevT) {
                ascending = false;
            }
            if (!Double.isNaN(prevT)) {
                double gap = Math.abs(tc - prevT);
                minGap = Math.min(minGap, gap);
                maxGap = Math.max(maxGap, gap);
            }
            prevT = tc;
        }

        boolean expectedAscending = segment.isHeat();
        if (ascending != expectedAscending && valid.size() > 1) {
            issues.add(new ConversionIssue("TEMPERATURE_ORDER", -1,
                    segment.segmentId() + " is declared " + segment.direction()
                            + " but temperature runs " + (ascending ? "ascending" : "descending")));
        }

        boolean signWasReversed = "EXO_UP".equalsIgnoreCase(segment.hfConvention());
        if (signWasReversed) {
            for (int idx : valid) {
                y[idx] = -y[idx];
            }
            issues.add(new ConversionIssue("REVERSED_HEATFLOW", -1,
                    segment.segmentId() + " recorded EXO_UP; canonical y flipped to endo-up"));
        }

        double[] vt = new double[valid.size()];
        double[] vy = new double[valid.size()];
        double[] vo = new double[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            int idx = valid.get(i);
            vt[i] = t[idx];
            vy[i] = y[idx];
            vo[i] = tOrig[idx];
        }
        boolean nonUniform = maxGap > 0 && minGap < Double.POSITIVE_INFINITY
                && (maxGap - minGap) / Math.max(maxGap, 1e-12) > 0.02;
        double uniformStep = maxGap == 0 ? Double.NaN : (maxGap + minGap) / 2.0;
        if (nonUniform) {
            issues.add(new ConversionIssue("NONUNIFORM_TEMPERATURE", -1,
                    segment.segmentId() + " grid gap ranges "
                            + String.format("%.4g..%.4g degC; integrating by actual trapezoids",
                            minGap, maxGap)));
        }

        Series series = new Series(segment.segmentId(), vt, vy, vo, unconverted,
                ascending, nonUniform ? Double.NaN : uniformStep, nonUniform);
        return new Normalized(series, signWasReversed, issues);
    }
}
