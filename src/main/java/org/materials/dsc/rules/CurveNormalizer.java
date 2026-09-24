package org.materials.dsc.rules;

import org.materials.dsc.domain.HeatFlowConvention;
import org.materials.dsc.domain.NormalizedCurve;
import org.materials.dsc.domain.RawPoint;
import org.materials.dsc.domain.ScanDirection;
import org.materials.dsc.domain.SourceDocs;
import org.materials.dsc.domain.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw instrument output -> normalized curve.
 *
 * Guarantees exercised by the case pack:
 *  - exo-up / endo-up is taken from the archived instrument property, never
 *    from the file name; an exo-up file yields reversedHeatFlow=true and is
 *    flipped to the internal endo-up axis;
 *  - non-uniform temperature sampling is measured, not repaired silently;
 *  - unconvertible values remain in place (usable=false) with diagnostics
 *    instead of being dropped for plotting;
 *  - observed scan direction is derived from the temperature axis and
 *    compared with the declared segment direction.
 */
public final class CurveNormalizer {

    /** relative spacing deviation that counts as non-uniform */
    public static final double SPACING_TOLERANCE = 0.25;

    public static final class Result {
        public final NormalizedCurve curve;
        public final List<Units.ConversionDiagnostic> diagnostics = new ArrayList<>();
        public boolean declaredMismatch;

        public Result(NormalizedCurve curve) {
            this.curve = curve;
        }
    }

    public Result normalize(SourceDocs.InstrumentOutput io,
                            SourceDocs.TemperatureCalibration cal,
                            ScanDirection declared) {
        int n = io.points.size();
        NormalizedCurve c = new NormalizedCurve(n);
        Result result = new Result(c);

        double off = 0.0;
        double slope = 1.0;
        if (cal != null) {
            Object[] o = Units.tryToBase(cal.offsetC == null ? "0" : cal.offsetC, "C", Units.Kind.TEMPERATURE);
            if (o[1] == null) {
                off = (Double) o[0];
            } else {
                result.diagnostics.add((Units.ConversionDiagnostic) o[1]);
            }
            Object[] s = Units.tryToBase(cal.slope == null ? "1" : cal.slope, "", Units.Kind.DIMENSIONLESS);
            if (s[1] == null) {
                slope = (Double) s[0];
            } else {
                result.diagnostics.add((Units.ConversionDiagnostic) s[1]);
            }
        }

        HeatFlowConvention conv = HeatFlowConvention.fromCode(io.signConvention);
        if (conv == null) {
            result.diagnostics.add(new Units.ConversionDiagnostic("UNKNOWN_SIGN_CONVENTION",
                    "instrument output " + io.id + " declares signConvention='"
                            + io.signConvention + "'; cannot decide endo/exo from file name, "
                            + "point kept on raw axis"));
            conv = HeatFlowConvention.ENDO_UP;
        }
        c.conventionCode = conv.code();
        c.reversedHeatFlow = conv == HeatFlowConvention.EXO_UP;
        int factor = conv.toEndoUpFactor();

        boolean hasTime = false;
        for (int i = 0; i < n; i++) {
            RawPoint p = io.points.get(i);
            boolean ok = true;

            Object[] t = Units.tryToBase(p.rawTemperature, p.tempUnit != null ? p.tempUnit : io.tempUnit,
                    Units.Kind.TEMPERATURE);
            if (t[1] != null) {
                result.diagnostics.add(withIndex((Units.ConversionDiagnostic) t[1], i));
                c.tempC[i] = Double.NaN;
                ok = false;
            } else {
                c.tempC[i] = (Double) t[0] * slope + off;
            }

            Object[] h = Units.tryToBase(p.rawHeatFlow, p.hfUnit != null ? p.hfUnit : io.hfUnit,
                    Units.Kind.HEAT_FLOW);
            if (h[1] != null) {
                result.diagnostics.add(withIndex((Units.ConversionDiagnostic) h[1], i));
                c.heatW[i] = Double.NaN;
                ok = false;
            } else {
                c.heatW[i] = (Double) h[0] * factor;
            }

            if (p.rawTime != null && !p.rawTime.isBlank()) {
                Object[] tm = Units.tryToBase(p.rawTime, p.timeUnit, Units.Kind.TIME);
                if (tm[1] != null) {
                    result.diagnostics.add(withIndex((Units.ConversionDiagnostic) tm[1], i));
                    c.timeS[i] = Double.NaN;
                } else {
                    c.timeS[i] = (Double) tm[0];
                    hasTime = true;
                }
            } else {
                c.timeS[i] = Double.NaN;
            }
            c.usable[i] = ok;
        }
        if (!hasTime) {
            for (int i = 0; i < n; i++) {
                c.timeS[i] = Double.NaN;
            }
        }

        measureSpacing(c);
        c.observedDirection = observeDirection(c);
        result.declaredMismatch = declared != null
                && declared != ScanDirection.UNKNOWN
                && c.observedDirection != ScanDirection.UNKNOWN
                && declared != c.observedDirection
                && declared != ScanDirection.ISOTHERMAL;
        return result;
    }

    private Units.ConversionDiagnostic withIndex(Units.ConversionDiagnostic d, int i) {
        return new Units.ConversionDiagnostic(d.code, "point[" + i + "]: " + d.message);
    }

    private void measureSpacing(NormalizedCurve c) {
        List<Double> gaps = new ArrayList<>();
        int prev = -1;
        for (int i = 0; i < c.tempC.length; i++) {
            if (!c.usable[i]) {
                continue;
            }
            if (prev >= 0) {
                gaps.add(Math.abs(c.tempC[i] - c.tempC[prev]));
            }
            prev = i;
        }
        if (gaps.size() < 3) {
            return;
        }
        List<Double> sorted = new ArrayList<>(gaps);
        sorted.sort(Double::compare);
        double median = sorted.get(sorted.size() / 2);
        int idx = 0;
        prev = -1;
        for (int i = 0; i < c.tempC.length; i++) {
            if (!c.usable[i]) {
                continue;
            }
            if (prev >= 0) {
                double g = Math.abs(c.tempC[i] - c.tempC[prev]);
                if (median > 1e-12 && Math.abs(g - median) > SPACING_TOLERANCE * median) {
                    c.nonUniformGaps.add(i);
                }
                idx++;
            }
            prev = i;
        }
    }

    private ScanDirection observeDirection(NormalizedCurve c) {
        int n = 0;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int i = 0; i < c.tempC.length; i++) {
            if (!c.usable[i]) {
                continue;
            }
            double x = i;
            double y = c.tempC[i];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
            n++;
        }
        if (n < 2) {
            return ScanDirection.UNKNOWN;
        }
        double den = n * sxx - sx * sx;
        if (den == 0) {
            return ScanDirection.ISOTHERMAL;
        }
        double b = (n * sxy - sx * sy) / den;
        double span = sy;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < c.tempC.length; i++) {
            if (c.usable[i]) {
                min = Math.min(min, c.tempC[i]);
                max = Math.max(max, c.tempC[i]);
            }
        }
        double range = max - min;
        if (range < 1e-6) {
            return ScanDirection.ISOTHERMAL;
        }
        return b > 0 ? ScanDirection.HEATING : ScanDirection.COOLING;
    }
}
