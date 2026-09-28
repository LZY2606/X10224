package com.materials.dsc.model;

/**
 * Every direct source the reviewer must inspect separately is archived under
 * its own kind instead of being folded into a single spreadsheet row.
 */
public enum SourceKind {
    INSTRUMENT_OUTPUT,
    SAMPLE_MASS,
    SCAN_RATE,
    ATMOSPHERE,
    TEMPERATURE_CALIBRATION,
    TANGENT_SELECTION
}
