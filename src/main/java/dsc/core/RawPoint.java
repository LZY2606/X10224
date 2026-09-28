package dsc.core;

/** A single instrument sample. Temperature/heat-flow are stored as imported, with units kept on the segment header. */
public record RawPoint(int index, Quantity temperature, Quantity heatFlow, long timeMs) {
    public boolean heatFlowConvertible() { return heatFlow != null && heatFlow.convertible(); }
}
