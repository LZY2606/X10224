package dsc.domain;

/**
 * A heating/cooling segment as found in the run. Detection is data-driven
 * (regression of temperature on time) and cross-checked against declared
 * metadata; the two must never be silently merged across a reversal.
 */
public record Segment(
        String id,
        String runId,
        int seq,
        ScanDirection detectedDirection,
        ScanDirection declaredDirection,
        double startTRaw,
        double endTRaw,
        String tempUnit,
        int fromOrd,
        int toOrd,
        boolean directionConsistent) {

    public boolean usable() {
        return directionConsistent && detectedDirection != ScanDirection.AMBIGUOUS;
    }
}
