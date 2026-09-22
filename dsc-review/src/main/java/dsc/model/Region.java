package dsc.model;

/**
 * A reviewer-selected temperature interval bound to exactly one segment.
 * An interval that straddles two segments/cycles is rejected at definition
 * time with a CYCLE_BOUNDARY judgment.
 */
public record Region(String regionId, String segmentId, TransitionKind kind,
                     double tLoCelsius, double tHiCelsius, String label,
                     String createdBy, int draftOfEventSeq) {
    public boolean contains(double tCelsius) {
        return tCelsius >= tLoCelsius - 1e-9 && tCelsius <= tHiCelsius + 1e-9;
    }
}
