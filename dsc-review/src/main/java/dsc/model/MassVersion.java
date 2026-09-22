package dsc.model;

import dsc.core.Quantity;
import dsc.core.RawValue;

import java.time.Instant;

/**
 * A mass entry has its own version. A revision never overwrites the old value:
 * all normalizations (specific heat flow, J/g enthalpy) derived under an
 * earlier mass become stale and keep pointing at the mass version they used.
 */
public record MassVersion(String massId, int version, RawValue rawMass, Quantity grams,
                          String reason, Instant recordedAt) {
    public double gramsValue() { return grams.value(); }
}
