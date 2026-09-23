package dsc.model;

/** A point in the normalized (calibrated degC, mW) derivation space. */
public record NormalizedPoint(int index, double timeS, double tC, double hfMW) {}
