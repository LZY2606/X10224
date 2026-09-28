package dsc.domain;

/**
 * Glass transition, crystallization or melt event located on one segment.
 * tg/deltaCp are set for glass steps; onset/peak/endset for peaks.
 */
public final class Transition {
    public final String id;
    public final String segmentId;
    public final String intervalId;
    public final String type;
    public final Direction direction;
    public final double onsetT, peakT, endsetT;
    public final double peakDeviationWg;
    public final Double tgMid, deltaCp;
    public final List<String> diagnostics;

    public Transition(String id, String segmentId, String intervalId, String type, Direction direction,
                      double onsetT, double peakT, double endsetT, double peakDeviationWg,
                      Double tgMid, Double deltaCp, List<String> diagnostics) {
        this.id = id;
        this.segmentId = segmentId;
        this.intervalId = intervalId;
        this.type = type;
        this.direction = direction;
        this.onsetT = onsetT;
        this.peakT = peakT;
        this.endsetT = endsetT;
        this.peakDeviationWg = peakDeviationWg;
        this.tgMid = tgMid;
        this.deltaCp = deltaCp;
        this.diagnostics = List.copyOf(diagnostics);
    }
}
