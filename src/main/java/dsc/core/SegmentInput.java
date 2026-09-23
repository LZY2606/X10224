package dsc.core;

import java.util.List;

/**
 * Archived instrument output for one scan segment of one sample history.
 *
 * The segment owns its declared polarity and thermal direction. Heating and
 * cooling segments are never merged; mixed/unknown directions are flagged.
 */
public record SegmentInput(
        String id,
        String sampleId,
        int cycle,
        ScanDirection direction,
        SignConvention polarity,
        String sourceFile,
        List<RawPoint> points,
        String note) {

    public SegmentInput {
        points = List.copyOf(points);
    }
}
