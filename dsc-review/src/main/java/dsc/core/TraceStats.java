package dsc.core;

import java.util.ArrayList;
import java.util.List;

/** Diagnostics about sampling regularity and direction reversals. */
public final class TraceStats {

    private final boolean nonUniformTemperatureSpacing;
    private final double medianDeltaTC;
    private final double maxDeltaTC;
    private final double minDeltaTC;
    private final ScanDirection direction;
    private final boolean reversalPresent;
    private final List<Integer> reversalRows = new ArrayList<>();
    private final int gapCount;

    public TraceStats(boolean nonUniformTemperatureSpacing, double medianDeltaTC,
                      double maxDeltaTC, double minDeltaTC, ScanDirection direction,
                      boolean reversalPresent, List<Integer> reversalRows, int gapCount) {
        this.nonUniformTemperatureSpacing = nonUniformTemperatureSpacing;
        this.medianDeltaTC = medianDeltaTC;
        this.maxDeltaTC = maxDeltaTC;
        this.minDeltaTC = minDeltaTC;
        this.direction = direction;
        this.reversalPresent = reversalPresent;
        this.reversalRows.addAll(reversalRows);
        this.gapCount = gapCount;
    }

    public boolean nonUniformTemperatureSpacing() { return nonUniformTemperatureSpacing; }
    public double medianDeltaTC() { return medianDeltaTC; }
    public double maxDeltaTC() { return maxDeltaTC; }
    public double minDeltaTC() { return minDeltaTC; }
    public ScanDirection direction() { return direction; }
    public boolean reversalPresent() { return reversalPresent; }
    public List<Integer> reversalRows() { return reversalRows; }
    public int gapCount() { return gapCount; }
}
