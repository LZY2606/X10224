package dsc.engine;

/** One sampled (temperature in degC, heat-flow in the instrument's native unit). */
public record Pt(double t, double hf) {
}
