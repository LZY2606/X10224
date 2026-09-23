package dsc.model;

import java.util.List;

/**
 * The normalized view of one instrument sequence: calibrated temperatures,
 * mW heat flow, declared + data-derived direction, monotone legs and
 * diagnostics. Built by the derivation layer only; raw imports are never rewritten.
 */
public record TraceView(
        String sequenceId,
        String sourceId,
        SegmentDirection direction,
        SignConvention signConvention,
        List<NormalizedPoint> points,
        List<SegmentLeg> legs,
        List<Diagnostic> diagnostics,
        String calibrationId
) {}
