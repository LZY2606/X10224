package dsc.model;

/** One raw DSC sample. x is time (s), t is temperature (degC), y is heat flow in raw unit. */
public record DscPoint(double x, double t, double y) {
}
