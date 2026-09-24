package io.dsc.model;

/**
 * One component of an enthalpy/temperature error budget. Stable code lets the
 * review UI link a displayed uncertainty back to its class of source.
 */
public record ErrorContribution(
        String code,          // ES-BASELINE / ES-MASS / ES-RATE / ES-CAL / ES-GRID / ES-RESOLUTION
        String label,
        double magnitude,
        String unit,
        double fractionOfTotal
) { }
