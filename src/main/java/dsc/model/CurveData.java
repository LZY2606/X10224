package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Derived, normalized view of one instrument output. Raw columns remain in
 * the source document; this layer carries canonical units (temperature in
 * kelvin, heat flow in mW, time in seconds) plus diagnostics for anything
 * that could not be converted.
 */
public final class CurveData {
    public final String sourceRef;
    public final double[] timeS;
    public final double[] tempK;
    public final double[] heatFlowMw;
    /** true when endotherm points up in the plotted signal, from declared metadata. */
    public final Boolean endoUp;
    public final List<Segment> segments;
    public final List<Diagnostic> diagnostics;

    public CurveData(String sourceRef, double[] timeS, double[] tempK, double[] heatFlowMw,
                     Boolean endoUp, List<Segment> segments, List<Diagnostic> diagnostics) {
        this.sourceRef = sourceRef;
        this.timeS = timeS;
        this.tempK = tempK;
        this.heatFlowMw = heatFlowMw;
        this.endoUp = endoUp;
        this.segments = segments == null ? List.of() : segments;
        this.diagnostics = diagnostics == null ? List.of() : diagnostics;
    }

    public List<Segment> byDirection(Segment.Direction d) {
        List<Segment> out = new ArrayList<>();
        for (Segment s : segments) {
            if (s.direction == d) out.add(s);
        }
        return out;
    }
}
