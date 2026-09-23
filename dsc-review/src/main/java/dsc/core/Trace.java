package dsc.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One heating or cooling sequence belonging to a single sample history.
 * A trace never mixes directions: a reversal inside the rows is reported as a
 * diagnostic and split/review is a reviewer decision, never an automatic merge.
 */
public final class Trace {

    private final String id;
    private final String sampleId;
    private final int cycleIndex;
    private final List<SamplePoint> points;
    private final SignConvention declaredConvention;
    private final TraceStats stats;

    public Trace(String id, String sampleId, int cycleIndex, List<SamplePoint> points,
                 SignConvention declaredConvention, TraceStats stats) {
        this.id = id;
        this.sampleId = sampleId;
        this.cycleIndex = cycleIndex;
        this.points = List.copyOf(points);
        this.declaredConvention = declaredConvention;
        this.stats = stats;
    }

    public String id() { return id; }
    public String sampleId() { return sampleId; }
    public int cycleIndex() { return cycleIndex; }
    public List<SamplePoint> points() { return points; }
    public SignConvention declaredConvention() { return declaredConvention; }
    public TraceStats stats() { return stats; }
    public ScanDirection direction() { return stats.direction(); }

    public int size() { return points.size(); }

    public double[] tempsC() {
        return points.stream().mapToDouble(SamplePoint::tempC).toArray();
    }

    public double[] heatFlowWg() {
        return points.stream().mapToDouble(SamplePoint::heatFlowWg).toArray();
    }

    public double[] timesS() {
        return points.stream().mapToDouble(SamplePoint::timeS).toArray();
    }

    /** Rows strictly inside the window; never wraps across a direction reversal. */
    public List<SamplePoint> rowsIn(TemperatureWindow window) {
        List<SamplePoint> out = new ArrayList<>();
        for (SamplePoint p : points) {
            if (!p.gap() && window.containsC(p.tempC())) out.add(p);
        }
        return out;
    }

    public static TraceStats computeStats(List<SamplePoint> points) {
        List<Double> dT = new ArrayList<>();
        int up = 0, down = 0, gaps = 0;
        double lastT = Double.NaN, lastTime = Double.NaN;
        List<Integer> reversals = new ArrayList<>();
        int sign = 0;
        for (SamplePoint p : points) {
            if (p.gap()) { gaps++; continue; }
            if (!Double.isNaN(lastT)) {
                double dt = p.timeS() - lastTime;
                double delta = p.tempC() - lastT;
                if (dt > 0) {
                    dT.add(Math.abs(delta));
                    if (delta > 1e-9) {
                        up++;
                        if (sign < 0) reversals.add(p.rowIndex());
                        sign = 1;
                    } else if (delta < -1e-9) {
                        down++;
                        if (sign > 0) reversals.add(p.rowIndex());
                        sign = -1;
                    }
                }
            }
            lastT = p.tempC();
            lastTime = p.timeS();
        }
        double[] sorted = dT.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = sorted.length == 0 ? Double.NaN : sorted[sorted.length / 2];
        double max = Arrays.stream(sorted).max().orElse(Double.NaN);
        double min = Arrays.stream(sorted).min().orElse(Double.NaN);
        boolean nonUniform = false;
        for (double v : sorted) {
            if (median > 0 && Math.abs(v - median) / median > 0.10) { nonUniform = true; break; }
        }
        ScanDirection dir;
        if (up > 0 && down > 0) dir = ScanDirection.MIXED;
        else if (up > 0) dir = ScanDirection.HEATING;
        else if (down > 0) dir = ScanDirection.COOLING;
        else dir = ScanDirection.UNKNOWN;
        return new TraceStats(nonUniform, median, max, min, dir, !reversals.isEmpty(), reversals, gaps);
    }
}
