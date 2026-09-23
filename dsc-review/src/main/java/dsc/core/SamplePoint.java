package dsc.core;

/**
 * One measured row. Temperatures are stored in the canonical Celsius basis after
 * conversion; gaps are NaN and the original token survives in RawCell archives.
 */
public final class SamplePoint {

    private final int rowIndex;
    private final double timeS;
    private final double tempC;
    private final double heatFlowWg;
    private final boolean gap;

    public SamplePoint(int rowIndex, double timeS, double tempC, double heatFlowWg, boolean gap) {
        this.rowIndex = rowIndex;
        this.timeS = timeS;
        this.tempC = tempC;
        this.heatFlowWg = heatFlowWg;
        this.gap = gap;
    }

    public int rowIndex() { return rowIndex; }
    public double timeS() { return timeS; }
    public double tempC() { return tempC; }
    public double heatFlowWg() { return heatFlowWg; }
    public boolean gap() { return gap; }
}
