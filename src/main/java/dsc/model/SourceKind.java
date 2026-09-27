package dsc.model;

/**
 * The six input categories that must be archived separately. They are never merged
 * into a single "upload" record, so a revision touches only its own dependency edges.
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT,
    SAMPLE_MASS,
    SCAN_RATE,
    ATMOSPHERE,
    TEMPERATURE_CALIBRATION,
    USER_TANGENT
}
