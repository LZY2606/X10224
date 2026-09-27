package dsc.domain;

/**
 * A user-selected tangent is archived as its own source, not merged into the
 * instrument output. Both tangents are given as indices into the raw curve,
 * preserving the original drag gesture.
 */
public record TangentSelection(
        String id,
        String transitionId,
        int version,
        int leftTangentFromOrd,
        int leftTangentToOrd,
        int rightTangentFromOrd,
        int rightTangentToOrd,
        String author,
        String comment,
        String eventId) {
}
