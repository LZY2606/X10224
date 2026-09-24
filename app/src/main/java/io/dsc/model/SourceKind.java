package io.dsc.model;

/**
 * Direct sources are archived SEPARATELY: instrument output, sample mass,
 * scan rate, atmosphere, temperature calibration and the user-selected tangent.
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT,
    SAMPLE_MASS,
    SCAN_RATE,
    ATMOSPHERE,
    TEMPERATURE_CALIBRATION,
    USER_TANGENT;

    public String code() { return name().toLowerCase(); }
}
