package dsc.domain;

/** Heat-flow vs temperature line: hf(mW) = slope * t(C) + intercept. */
public record Line(double slope, double intercept) {
    public double at(double tC) { return slope * tC + intercept; }
    public static Line fit(double[] t, double[] y) {
        int n = t.length;
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int k = 0; k < n; k++) { sx += t[k]; sy += y[k]; sxx += t[k]*t[k]; sxy += t[k]*y[k]; }
        double den = n * sxx - sx * sx;
        if (Math.abs(den) < 1e-18) return new Line(0, sy / n);
        double slope = (n * sxy - sx * sy) / den;
        return new Line(slope, (sy - slope * sx) / n);
    }
    public double intersection(Line other) {
        if (Math.abs(slope - other.slope) < 1e-15) return Double.NaN;
        return (other.intercept - intercept) / (slope - other.slope);
    }
}
