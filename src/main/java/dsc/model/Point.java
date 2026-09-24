package dsc.model;

/**
 * One raw instrument sample, exactly as imported:
 * time (s), temperature (degC, basis sample-temperature), heat flow (mW).
 * Temperature spacing is NOT assumed uniform.
 */
public record Point(double timeS, double temperatureC, double heatFlowMw) {}
