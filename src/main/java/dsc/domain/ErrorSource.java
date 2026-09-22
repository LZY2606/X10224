package dsc.domain;

/**
 * One contributor to the uncertainty of a derived value. Every displayed
 * derived value reads these back from the archive so reviewers can see the
 * provenance of the error, not just a number.
 */
public final class ErrorSource {
    public String code;        // TEMPERATURE_CALIBRATION | BASELINE_CHOICE | SCAN_RATE | MASS_VERSION | EMPTY_INTERVAL | GRID_SPACING
    public String sourceId;    // stable id of the source/candidate responsible
    public String detail;
    public double contribution; // propagated magnitude in the value's unit
    public String unit;

    public ErrorSource() {}
    public ErrorSource(String code, String sourceId, String detail, double contribution, String unit) {
        this.code = code; this.sourceId = sourceId; this.detail = detail;
        this.contribution = contribution; this.unit = unit;
    }
}
