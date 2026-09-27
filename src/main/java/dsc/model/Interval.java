package dsc.model;

/** A user-draggable temperature window, locked to one scan segment. */
public record Interval(
        String intervalId,
        String projectId,
        String segmentId,
        String label,
        double startC,
        double endC,
        long createdSeq
) {
    public Interval moved(double newStartC, double newEndC) {
        return new Interval(intervalId, projectId, segmentId, label, newStartC, newEndC, createdSeq);
    }
}
