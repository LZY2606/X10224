package dsc.model;

import dsc.core.Quantity;

import java.time.Instant;

/**
 * Temperature calibration archived separately. Derived temperatures are
 * shifted by {@code offsetCelsius}; a calibration change invalidates every
 * temperature-dependent result.
 */
public record Calibration(String calibrationId, String standard, Quantity offsetCelsius,
                          Instant calibratedAt, String note) {}
