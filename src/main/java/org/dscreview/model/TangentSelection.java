package org.dscreview.model;

import java.util.List;

/**
 * A user-chosen tangent: two anchor ranges (by point index) plus a slope,
 * archived independently from the instrument output.
 */
public record TangentSelection(
        String tangentId,
        String segmentId,
        String createdBy,
        String createdAt,
        Anchor pre,
        Anchor post,
        double slopeMilliwattPerK,
        String note) {

    public record Anchor(int startIndex, int endIndex, double temperatureK, double heatFlowMw) {}
}
