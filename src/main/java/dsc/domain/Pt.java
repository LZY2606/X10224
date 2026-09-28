package dsc.domain;

/** One sample of an instrument output curve (temperature degC, heat flux W/g). */
public record Pt(double t, double y) {}
