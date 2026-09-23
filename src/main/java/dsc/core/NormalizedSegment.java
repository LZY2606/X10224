package dsc.core;

import java.util.List;

/**
 * Canonical derivation of one instrument segment: polarity mapped to
 * endo-up, temperatures in K, heat flow in W and W/g. Keeps its own
 * diagnostics and provenance stamps.
 */
public record NormalizedSegment(
        String segmentId,
        ScanDirection direction,
        String massId,
        String rateId,
        String calibrationId,
        double massG,
        double rateKs,
        List<NormalizedPoint> points,
        List<Diagnostic> diagnostics) {

    public NormalizedSegment {
        points = List.copyOf(points);
        diagnostics = List.copyOf(diagnostics);
    }
}
