package dsc.model;

import dsc.core.Quantity;
import dsc.core.RawValue;

/** Atmosphere is archived separately from the instrument output. */
public record Atmosphere(String atmosphereId, String gas, RawValue flowRate, Quantity flowNormalized,
                         String note) {}
