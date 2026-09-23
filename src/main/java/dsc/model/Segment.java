package dsc.model;

import java.util.List;

/**
 * A scan segment inside one sample history (a heating, cooling or
 * re-heating leg of a thermal cycle). Segments keep the instrument trace
 * source id and the direction decided for them; heating and cooling legs
 * are never mixed inside one baseline / transition computation.
 */
public record Segment(
        String id,
        String studyId,
        int order,
        String cycle,
        Direction direction,
        String directionBasis,
        String traceSourceId,
        String rateSourceId,
        String atmosphereSourceId,
        String calibrationSourceId,
        double tStartK,
        double tEndK,
        int pointCount,
        boolean reversedSignal,
        boolean nonUniform,
        List<String> diagnostics
) {

    public Segment withDirection(Direction d, String basis) {
        return new Segment(id, studyId, order, cycle, d, basis, traceSourceId, rateSourceId,
                atmosphereSourceId, calibrationSourceId, tStartK, tEndK, pointCount,
                reversedSignal, nonUniform, diagnostics);
    }

    public Segment withDiagnostics(List<String> diags) {
        return new Segment(id, studyId, order, cycle, direction, directionBasis, traceSourceId,
                rateSourceId, atmosphereSourceId, calibrationSourceId, tStartK, tEndK,
                pointCount, reversedSignal, nonUniform, diags);
    }
}
