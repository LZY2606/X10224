package org.dscreview.num;

import java.util.ArrayList;
import java.util.List;
import org.dscreview.model.Diagnostic;
import org.dscreview.model.DscPoint;
import org.dscreview.model.Segment;
import static org.dscreview.model.DomainEnums.ScanDirection;
import static org.dscreview.model.DomainEnums.SignConvention;

/**
 * Normalized representation of a segment used only by the derived layer:
 * samples in (time[s], temperature[degC], heatflow[mW], endo-up convention).
 * Points that cannot be converted are retained in the raw segment and reported
 * here as diagnostics; they are never silently dropped for plotting.
 */
public record CurveSeries(
        String segmentId,
        ScanDirection direction,
        SignConvention signConvention,
        double[] timeS,
        double[] tDegC,
        double[] hfMw,           // normalized to ENDO_UP (endotherm = positive deviation)
        boolean[] usable,
        List<Diagnostic> diagnostics) {

    /** Convert raw instrument heat flow to an endo-up mW signal. */
    public static double normalizeSign(double hfMw, SignConvention c) {
        return c == SignConvention.EXO_UP ? -hfMw : hfMw;
    }

    public static CurveSeries from(Segment seg) {
        int n = seg.points().size();
        double[] tm = new double[n], tC = new double[n], hf = new double[n];
        boolean[] ok = new boolean[n];
        List<Diagnostic> diag = new ArrayList<>();
        boolean monotoneUp = true, monotoneDown = true;
        double prevT = Double.NaN;
        boolean nonUniform = false, prevDt = false;
        double firstDt = Double.NaN;
        for (int i = 0; i < n; i++) {
            DscPoint p = seg.points().get(i);
            tm[i] = p.timeS();
            double tc = Units.toDegC(p.temperature());
            double hRaw = Units.toMilliwatt(p.heatFlow());
            tC[i] = tc; hf[i] = hRaw;
            boolean good = p.usable() && !Double.isNaN(tc) && !Double.isNaN(hRaw);
            ok[i] = good;
            if (!good) {
                diag.add(new Diagnostic("UNCONVERTIBLE_POINT", "warn",
                        "Point #" + i + " kept raw: " + p.diagnostic(),
                        seg.segmentId() + "#" + i));
            }
            if (good) {
                if (!Double.isNaN(prevT)) {
                    if (tc < prevT) monotoneUp = false;
                    if (tc > prevT) monotoneDown = false;
                    double dt = tc - prevT;
                    if (Double.isNaN(firstDt)) firstDt = dt;
                    else if (Math.abs(dt - firstDt) > 1e-6) nonUniform = true;
                }
                prevT = tc;
            }
        }
        ScanDirection dataDir = monotoneUp && !monotoneDown ? ScanDirection.HEATING
                : monotoneDown && !monotoneUp ? ScanDirection.COOLING
                : ScanDirection.UNKNOWN;
        if (dataDir != seg.declaredDirection()) {
            diag.add(new Diagnostic("DIRECTION_CONFLICT", "error",
                    "Declared " + seg.declaredDirection() + " but temperature trend is "
                            + dataDir + "; segment not mixed with the other branch", seg.segmentId()));
        }
        if (nonUniform) {
            diag.add(new Diagnostic("NONUNIFORM_GRID", "info",
                    "Non-equal temperature spacing; integration uses the actual samples.", seg.segmentId()));
        }
        SignConvention c = seg.signConvention();
        if (c == SignConvention.UNKNOWN) {
            diag.add(new Diagnostic("INDETERMINATE_SIGN", "error",
                    "Endo/exo direction is not declared; it cannot be guessed from the file name "
                            + seg.originalFileName(), seg.segmentId()));
        }
        double[] endoUp = new double[n];
        for (int i = 0; i < n; i++) endoUp[i] = Double.isNaN(hf[i]) ? Double.NaN : normalizeSign(hf[i], c);
        return new CurveSeries(seg.segmentId(), dataDir, c, tm, tC, endoUp, ok, diag);
    }

    /** Indexes of usable points inside [from,to] degC; direction-independent containment. */
    public int[] indicesInWindow(double from, double to) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < tDegC.length; i++) {
            if (!usable[i]) continue;
            double t = tDegC[i];
            if (t >= Math.min(from, to) - 1e-9 && t <= Math.max(from, to) + 1e-9) out.add(i);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }
}
