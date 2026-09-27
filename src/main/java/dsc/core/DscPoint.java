package dsc.core;

/** One raw sample. All quantities keep their original units (s, degC, mW). */
public record DscPoint(double timeS, double tempC, double heatFlowMw) {}
