package dsc;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Pure DSC numerics. All quantities carry explicit coordinates: temperature
 * in degC, time in seconds, heat flow in mW. Integration is trapezoidal over
 * the actual sample times, so non-uniform temperature spacing is handled
 * exactly instead of being assumed away.
 */
public final class DscMath {
    private DscMath() {}

    public record Curve(double[] timeS, double[] tempC, double[] hfMw) {
        public int size() { return timeS.length; }
    }

    public record Line(double slope, double intercept) {
        public double at(double t) { return slope * t + intercept; }
    }

    /** least-squares line over points whose temperature lies in [lo, hi] */
    public static Line fitLine(Curve c, double lo, double hi) {
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        int n = 0;
        for (int i = 0; i < c.size(); i++) {
            double t = c.tempC()[i];
            if (t < lo || t > hi) continue;
            sx += t; sy += c.hfMw()[i]; sxx += t * t; sxy += t * c.hfMw()[i];
            n++;
        }
        if (n < 2) return null;
        double denom = n * sxx - sx * sx;
        if (Math.abs(denom) < 1e-12) return new Line(0, sy / n);
        double slope = (n * sxy - sx * sy) / denom;
        return new Line(slope, (sy - slope * sx) / n);
    }

    public static double[] linearBaseline(Curve c, double t1, double t2) {
        double h1 = interp(c, t1), h2 = interp(c, t2);
        double[] out = new double[c.size()];
        for (int i = 0; i < out.length; i++) {
            double f = (c.tempC()[i] - t1) / (t2 - t1);
            out[i] = h1 + f * (h2 - h1);
        }
        return out;
    }

    /** sigmoidal baseline blending pre/post tangents; standard for glass transitions */
    public static double[] sigmoidalBaseline(Curve c, Line left, Line right, double t1, double t2) {
        double mid = (t1 + t2) / 2.0;
        double k = Math.max((t2 - t1) / 10.0, 1e-6);
        double[] out = new double[c.size()];
        for (int i = 0; i < out.length; i++) {
            double t = c.tempC()[i];
            double w = 1.0 / (1.0 + Math.exp(-(t - mid) / k));
            out[i] = (1 - w) * left.at(t) + w * right.at(t);
        }
        return out;
    }

    public static double[] lineBaseline(Curve c, Line line) {
        double[] out = new double[c.size()];
        for (int i = 0; i < out.length; i++) out[i] = line.at(c.tempC()[i]);
        return out;
    }

    public static double interp(Curve c, double temp) {
        double[] tc = c.tempC();
        for (int i = 0; i < tc.length - 1; i++) {
            double a = tc[i], b = tc[i + 1];
            if ((temp - a) * (temp - b) <= 0 && a != b) {
                double f = (temp - a) / (b - a);
                return c.hfMw()[i] + f * (c.hfMw()[i + 1] - c.hfMw()[i]);
            }
        }
        return c.hfMw()[nearest(c, temp)];
    }

    public static int nearest(Curve c, double temp) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < c.size(); i++) {
            double d = Math.abs(c.tempC()[i] - temp);
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    public static int countInInterval(Curve c, double t1, double t2) {
        double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
        int n = 0;
        for (double t : c.tempC()) if (t >= lo && t <= hi) n++;
        return n;
    }

    /** intersection temperature of two lines, or NaN if parallel */
    public static double intersect(Line a, Line b) {
        if (Math.abs(a.slope() - b.slope()) < 1e-12) return Double.NaN;
        return (b.intercept() - a.intercept()) / (a.slope() - b.slope());
    }

    /** local tangent at the steepest point of (hf - baseline) within [lo,hi], first or second half */
    public static Line inflectionTangent(Curve c, double[] baseline, double lo, double hi, boolean firstHalf) {
        double mid = (lo + hi) / 2.0;
        int best = -1;
        double bestSlope = 0;
        for (int i = 1; i < c.size() - 1; i++) {
            double t = c.tempC()[i];
            if (t < lo || t > hi) continue;
            if (firstHalf && t > mid) continue;
            if (!firstHalf && t < mid) continue;
            double dt = c.tempC()[i + 1] - c.tempC()[i - 1];
            if (Math.abs(dt) < 1e-12) continue;
            double s = ((c.hfMw()[i + 1] - baseline[i + 1]) - (c.hfMw()[i - 1] - baseline[i - 1])) / dt;
            if (best < 0 || Math.abs(s) > Math.abs(bestSlope)) { best = i; bestSlope = s; }
        }
        if (best < 0) return null;
        double t0 = c.tempC()[best];
        double y0 = c.hfMw()[best] - baseline[best] + baseline[best];
        return new Line(bestSlope, y0 - bestSlope * t0);
    }

    /**
     * Peak temperature: extremum of (hf - baseline) whose sign matches the
     * physical expectation from event kind + instrument sign convention.
     * Returns [peakTemp, observedSign, expectedSign].
     */
    public static double[] peak(Curve c, double[] baseline, double lo, double hi, int expectedSign) {
        int best = -1;
        double bestAbs = 0;
        for (int i = 0; i < c.size(); i++) {
            double t = c.tempC()[i];
            if (t < lo || t > hi) continue;
            double dev = c.hfMw()[i] - baseline[i];
            if (Math.signum(dev) == Math.signum(expectedSign) && Math.abs(dev) > bestAbs) {
                best = i; bestAbs = Math.abs(dev);
            }
        }
        if (best < 0) {
            // no point with the expected sign: fall back to largest deviation, flag via sign mismatch
            for (int i = 0; i < c.size(); i++) {
                double t = c.tempC()[i];
                if (t < lo || t > hi) continue;
                double dev = c.hfMw()[i] - baseline[i];
                if (Math.abs(dev) > bestAbs) { best = i; bestAbs = Math.abs(dev); }
            }
        }
        if (best < 0) return new double[]{Double.NaN, 0, expectedSign};
        double dev = c.hfMw()[best] - baseline[best];
        return new double[]{c.tempC()[best], Math.signum(dev), expectedSign};
    }

    /** trapezoidal integral of (hf - baseline) over time in [t1,t2] degC; mW*s = mJ */
    public static double integrateMj(Curve c, double[] baseline, double t1, double t2) {
        double lo = Math.min(t1, t2), hi = Math.max(t1, t2);
        double area = 0;
        for (int i = 0; i < c.size() - 1; i++) {
            double ta = c.tempC()[i], tb = c.tempC()[i + 1];
            double tlo = Math.min(ta, tb), thi = Math.max(ta, tb);
            if (thi < lo || tlo > hi) continue;
            double ya = c.hfMw()[i] - baseline[i];
            double yb = c.hfMw()[i + 1] - baseline[i + 1];
            double dt = c.timeS()[i + 1] - c.timeS()[i];
            double frac = 1.0;
            if (tlo < lo || thi > hi) {
                double span = thi - tlo;
                if (span > 1e-12) frac = (Math.min(thi, hi) - Math.max(tlo, lo)) / span;
            }
            area += 0.5 * (ya + yb) * dt * frac;
        }
        return area;
    }

    /** true when sample spacing in temperature is not constant */
    public static boolean nonUniformSpacing(Curve c) {
        double ref = -1;
        for (int i = 1; i < c.size(); i++) {
            double d = Math.abs(c.tempC()[i] - c.tempC()[i - 1]);
            if (d < 1e-12) continue;
            if (ref < 0) { ref = d; continue; }
            if (Math.abs(d - ref) / ref > 0.05) return true;
        }
        return false;
    }

    /** expected deviation sign: +1 means transition appears as upward peak */
    public static int expectedSign(String kind, String signConvention) {
        boolean endoUp = "ENDO_UP".equals(signConvention);
        boolean endothermic = switch (kind) {
            case "MELTING", "GLASS" -> true;
            case "CRYSTALLIZATION" -> false;
            default -> throw new IllegalArgumentException("unknown event kind " + kind);
        };
        return endothermic == endoUp ? 1 : -1;
    }

    public static ObjectNode errorSources(double enthalpyJg, double massMg, double massUncMg,
                                          double calFactor, double baselineSpreadJg,
                                          boolean nonUniform) {
        ArrayNode list = Json.arr();
        list.add(src("BASELINE_SPREAD", baselineSpreadJg,
                "并列基线候选间焓变差的一半，保留多解差异"));
        double massErr = Math.abs(enthalpyJg) * (massUncMg / massMg);
        list.add(src("MASS_UNCERTAINTY", massErr,
                "样品质量 ±" + massUncMg + " mg 传播到归一化焓"));
        double calErr = Math.abs(enthalpyJg) * Math.abs(1 - calFactor) + Math.abs(enthalpyJg) * 0.005;
        list.add(src("CALIBRATION_FACTOR", calErr,
                "焓标定因子 " + calFactor + " 及其 ±0.5% 不确定度"));
        if (nonUniform) {
            list.add(src("NON_UNIFORM_SPACING", Math.abs(enthalpyJg) * 0.001,
                    "温度非等间隔采样：按实际时间梯形积分，残余按 0.1% 估计"));
        }
        ObjectNode out = Json.obj();
        out.set("sources", list);
        double total = 0;
        for (var n : list) total += n.get("absJg").doubleValue() * n.get("absJg").doubleValue();
        out.put("totalAbsJg", Math.sqrt(total));
        return out;
    }

    private static ObjectNode src(String code, double absJg, String note) {
        ObjectNode n = Json.obj();
        n.put("code", code);
        n.put("absJg", absJg);
        n.put("note", note);
        return n;
    }
}
