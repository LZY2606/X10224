package dsc.model;

import dsc.qty.HeatFlowUnit;
import dsc.qty.SignConvention;
import dsc.qty.TempUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One instrument output segment in a sample history. Segment id is stable across
 * export/import. Heat/cool direction is computed from the axis, not the file name.
 */
public final class RawTrace {
    private final String segmentId;
    private final String sourceName;
    private final String declaredKind;
    private final TempUnit tempUnit;
    private final HeatFlowUnit heatUnit;
    private final SignConvention signConvention;
    private final double rateCPerMin;
    private final Atmosphere atmosphere;
    private final TempCalibration calibration;
    private final List<TracePoint> points;
    private final List<Diagnostic> diagnostics;
    private final ScanDirection direction;
    private final boolean equidistant;
    private final double minSpacingC;
    private final double maxSpacingC;

    public RawTrace(String segmentId, String sourceName, String declaredKind,
                    TempUnit tempUnit, HeatFlowUnit heatUnit, SignConvention signConvention,
                    double rateCPerMin, Atmosphere atmosphere, TempCalibration calibration,
                    List<TracePoint> points, List<Diagnostic> diagnostics) {
        this.segmentId = segmentId;
        this.sourceName = sourceName;
        this.declaredKind = declaredKind;
        this.tempUnit = tempUnit;
        this.heatUnit = heatUnit;
        this.signConvention = signConvention;
        this.rateCPerMin = rateCPerMin;
        this.atmosphere = atmosphere;
        this.calibration = calibration;
        this.points = Collections.unmodifiableList(new ArrayList<>(points));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));

        ScanDirection dir = ScanDirection.UNKNOWN;
        boolean equi = true;
        double minSp = Double.POSITIVE_INFINITY;
        double maxSp = 0.0;
        int up = 0;
        int down = 0;
        Double prev = null;
        for (TracePoint p : points) {
            if (!p.temp.convertible()) {
                continue;
            }
            double t = p.tempC();
            if (prev != null) {
                double d = t - prev;
                if (d > 1e-9) {
                    up++;
                    double sp = Math.abs(d);
                    minSp = Math.min(minSp, sp);
                    maxSp = Math.max(maxSp, sp);
                } else if (d < -1e-9) {
                    down++;
                    double sp = Math.abs(d);
                    minSp = Math.min(minSp, sp);
                    maxSp = Math.max(maxSp, sp);
                }
            }
            prev = t;
        }
        if (up > 0 && down > 0) {
            dir = ScanDirection.MIXED;
        } else if (up > 0) {
            dir = ScanDirection.HEATING;
        } else if (down > 0) {
            dir = ScanDirection.COOLING;
        }
        if (minSp != Double.POSITIVE_INFINITY && maxSp / Math.max(minSp, 1e-12) > 1.25) {
            equi = false;
        }
        this.direction = dir;
        this.equidistant = equi;
        this.minSpacingC = minSp == Double.POSITIVE_INFINITY ? Double.NaN : minSp;
        this.maxSpacingC = maxSp;
    }

    public String segmentId() { return segmentId; }
    public String sourceName() { return sourceName; }
    public String declaredKind() { return declaredKind; }
    public TempUnit tempUnit() { return tempUnit; }
    public HeatFlowUnit heatUnit() { return heatUnit; }
    public SignConvention signConvention() { return signConvention; }
    public double rateCPerMin() { return rateCPerMin; }
    public Atmosphere atmosphere() { return atmosphere; }
    public TempCalibration calibration() { return calibration; }
    public List<TracePoint> points() { return points; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public ScanDirection direction() { return direction; }
    public boolean equidistant() { return equidistant; }
    public double minSpacingC() { return minSpacingC; }
    public double maxSpacingC() { return maxSpacingC; }
}
