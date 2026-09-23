package dsc.model;

/**
 * A user-selected tangent: two anchor temperature regions (declared in the
 * trace's native basis) defining a baseline, archived independently from the
 * instrument output.
 */
public record TangentSelection(
        String tangentId,
        String sequenceId,
        String intervalId,
        double leftStartC, double leftEndC,
        double rightStartC, double rightEndC,
        BaselineType type,
        String note,
        int revision
) {}
