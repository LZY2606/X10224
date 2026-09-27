package dsc.model;

/**
 * A monotonic heating or cooling run inside one instrument file. Direction is detected
 * from the temperature trend, never inferred from the file name. Cycle labels such as
 * H1/C1/H2 identify the scan-history position; baselines and integrals never span two
 * segments.
 */
public record Segment(
        String segmentId,
        String cycleLabel,
        String detectedDirection,
        String declaredDirection,
        int fromIndex,
        int toIndex,
        double minC,
        double maxC,
        boolean nonUniformSpacing
) {
}
