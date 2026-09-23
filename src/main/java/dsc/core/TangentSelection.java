package dsc.core;

/**
 * Archived user-selected tangents for one transition interval. Tangent pairs
 * anchor the tangent/linear baseline candidate. They are separate from the
 * instrument output and are an independent provenance source.
 */
public record TangentSelection(String id, String segmentId, String intervalId,
                               Qty leftT, Qty rightT,
                               Qty leftHf, Qty rightHf,
                               String recordedAt, String note) {
}
