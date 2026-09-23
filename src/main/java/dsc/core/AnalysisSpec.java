package dsc.core;

/**
 * One requested transition integration on one segment. The segment direction is
 * carried explicitly so heat/cool can never be mixed by joining intervals.
 */
public record AnalysisSpec(
        String analysisId,
        String seriesId,
        String direction,
        int cycle,
        TransitionType type,
        Interval interval,
        TangentSpec tangent
) {

    public boolean sameSegment(String sid, String dir, int cycle) {
        return seriesId.equals(sid)
                && direction.equalsIgnoreCase(dir)
                && this.cycle == cycle;
    }
}
