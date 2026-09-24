package dsc.domain;

/**
 * A single heating or cooling scan inside one sample history. Segments are
 * never mixed: baselines and integrals are always computed within one segment.
 * The declared {@code directionHint} (from the instrument program) is checked
 * against the actual temperature progression; disagreement is a diagnostic,
 * not a silent flip.
 */
public record Segment(
        String id,
        int cycleIndex,
        String label,
        ScanDirection declaredDirection,
        RawData raw,
        Metadata metadata
) {
    public ScanDirection observedDirection() {
        return ScanDirection.fromProgression(raw.temperature());
    }
}
