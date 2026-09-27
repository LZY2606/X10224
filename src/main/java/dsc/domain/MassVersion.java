package dsc.domain;

/**
 * A mass entry in the sample history. Every normalised derivative references
 * the exact mass version it used; a new mass version marks all such derivatives
 * stale (MASS_REVISION_INVALIDATES rule).
 */
public record MassVersion(
        String id,
        String sampleId,
        int ord,
        Qty mass,
        String reason,
        String effectiveFromEventId) {

    public double grams() {
        return mass.to("g");
    }
}
