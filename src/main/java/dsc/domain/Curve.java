package dsc.domain;

import java.util.List;

/**
 * Instrument output, kept exactly as imported. points are original-order samples;
 * non-uniform spacing is legal and preserved.
 */
public final class Curve {
    public final String id;
    public final String segmentId;
    public final String instrument;
    public final String signConvention;
    public final List<String> tempRawColumn;
    public final List<String> fluxRawColumn;
    public final List<Pt> points;
    public final List<String> diagnostics;

    public Curve(String id, String segmentId, String instrument, String signConvention,
                 List<String> tempRawColumn, List<String> fluxRawColumn,
                 List<Pt> points, List<String> diagnostics) {
        this.id = id;
        this.segmentId = segmentId;
        this.instrument = instrument;
        this.signConvention = signConvention;
        this.tempRawColumn = List.copyOf(tempRawColumn);
        this.fluxRawColumn = List.copyOf(fluxRawColumn);
        this.points = List.copyOf(points);
        this.diagnostics = List.copyOf(diagnostics);
    }
}
