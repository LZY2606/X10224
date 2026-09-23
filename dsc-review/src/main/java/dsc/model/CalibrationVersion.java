package dsc.model;

/**
 * Temperature calibration: tCalibrated = tRaw*slope + offset. Carries its own
 * version and uncertainty; a change invalidates traces built from it.
 */
public record CalibrationVersion(String calibrationId, double slope, double offsetC,
                                 double uncertaintyC, String basis, int revision) {
    public double apply(double rawCelsius) { return rawCelsius * slope + offsetC; }
}
