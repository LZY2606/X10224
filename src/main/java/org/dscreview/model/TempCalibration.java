package org.dscreview.model;

import java.util.List;

/** Separately archived temperature calibration (standards and corrections). */
public record TempCalibration(
        String calibrationId,
        Quant offsetK,
        List<Quant> referenceTemperatures,
        String performedAt,
        String method,
        List<Diagnostic> diagnostics) {
}
