package dsc.model;

import dsc.core.Quantity;
import dsc.core.RawValue;
import dsc.core.Units;

import java.util.Collections;
import java.util.List;

/**
 * A single heating/cooling/isothermal segment inside one sample history.
 * The direction is computed from the measured temperature progression; it is
 * never inferred from the source file name.
 */
public final class ScanSegment {
    private final String segmentId;
    private final String sourceFile;
    private final int cycle;
    private final SignalConvention convention;
    private final Quantity nominalRate;
    private final List<SamplePoint> points;
    private final Direction direction;

    public ScanSegment(String segmentId, String sourceFile, int cycle, SignalConvention convention,
                       Quantity nominalRate, List<SamplePoint> points) {
        this.segmentId = segmentId;
        this.sourceFile = sourceFile;
        this.cycle = cycle;
        this.convention = convention;
        this.nominalRate = nominalRate;
        this.points = points;
        this.direction = computeDirection(points);
    }

    private static Direction computeDirection(List<SamplePoint> ps) {
        Quantity first = null, last = null;
        for (SamplePoint p : ps) {
            if (p.plottable()) { if (first == null) first = p.tCelsius(); last = p.tCelsius(); }
        }
        if (first == null) return Direction.NEUTRAL;
        return Direction.fromSlope(last.value() - first.value());
    }

    public String segmentId() { return segmentId; }
    public String sourceFile() { return sourceFile; }
    public int cycle() { return cycle; }
    public SignalConvention convention() { return convention; }
    public Quantity nominalRate() { return nominalRate; }
    public List<SamplePoint> points() { return Collections.unmodifiableList(points); }
    public Direction direction() { return direction; }

    /** Points normalized to canonical units; unparseable points are excluded. */
    public List<SamplePoint> plottablePoints() {
        return points.stream().filter(SamplePoint::plottable).toList();
    }

    public boolean nonUniformTemperatures() {
        List<SamplePoint> ps = plottablePoints();
        if (ps.size() < 3) return false;
        double first = Math.abs(ps.get(1).tCelsius().value() - ps.get(0).tCelsius().value());
        for (int i = 2; i < ps.size(); i++) {
            double step = Math.abs(ps.get(i).tCelsius().value() - ps.get(i - 1).tCelsius().value());
            if (first > 1e-9 && Math.abs(step - first) / first > 0.05) return true;
        }
        return false;
    }

    public double nominalRateCelsiusPerMin() {
        return nominalRate == null ? Double.NaN : nominalRate.valueIn(Units.C_PER_MIN);
    }
}
