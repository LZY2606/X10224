package dsc.domain;

/** One itemised error source attached to an enthalpy result. */
public record ErrorContribution(String sourceId, String kind, double relativeFraction, String detail) {
}
