package org.materials.dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * The normalized view of an instrument output: SI-ish units, endotherm-up,
 * calibrated temperatures, with per-point diagnostics.  It is a *derived*
 * artifact; the raw document remains archived and unchanged.
 */
public final class NormalizedCurve {
    /** calibrated temperature in °C; NaN for unconvertible points */
    public final double[] tempC;
    /** endo-up heat flow in W; NaN for unconvertible points */
    public final double[] heatW;
    /** time in s, may be null column when absent */
    public final double[] timeS;
    public final boolean[] usable;
    public final List<String> pointDiagnostics = new ArrayList<>();
    /** index gaps where temperature spacing is non-uniform beyond tolerance */
    public final List<Integer> nonUniformGaps = new ArrayList<>();
    public ScanDirection observedDirection = ScanDirection.UNKNOWN;
    public boolean reversedHeatFlow;
    public String conventionCode;

    public NormalizedCurve(int n) {
        tempC = new double[n];
        heatW = new double[n];
        timeS = new double[n];
        usable = new boolean[n];
    }
}
