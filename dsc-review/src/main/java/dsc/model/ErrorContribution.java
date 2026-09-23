package dsc.model;

/** One leg of the enthalpy error budget, each carrying its own unit. */
public record ErrorContribution(String code, String sourceId, double magnitude, String unit, String explanation) {}
