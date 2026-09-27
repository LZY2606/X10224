package dsc.model;

/** A single instrument sample. All coordinates keep their original tokens and units. */
public record CurvePoint(int index, Q temperature, Q heatFlow, Q time, String rawLine) {
}
