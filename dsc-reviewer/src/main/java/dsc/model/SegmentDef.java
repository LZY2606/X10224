package dsc.model;

import java.util.List;

/**
 * A single temperature-program segment (one heat or cool leg) as archived from instrument output.
 *
 * @param segmentId     stable id from the case package (segment identity is not inferred from
 *                      file name; the declared program direction is authoritative)
 * @param cycleIndex    thermal cycle the segment belongs to (heat/cool legs are never mixed
 *                      across cycle boundaries)
 * @param direction     declared scan direction: HEAT or COOL
 * @param declaredRate  scan rate exactly as declared (unit preserved)
 * @param hfConvention  instrument sign convention declared by metadata: EXO_UP means positive
 *                      heat flow is exothermic, ENDO_UP means positive is endothermic.
 *                      Never guessed from file names.
 * @param tempUnit      temperature unit declared for the column
 * @param hfUnit        heat-flow unit declared for the column
 * @param points        raw rows in source order
 */
public record SegmentDef(String segmentId, int cycleIndex, String direction,
                         Quant declaredRate, String hfConvention,
                         String tempUnit, String hfUnit,
                         List<RawPoint> points) {

    public boolean isHeat() {
        return "HEAT".equalsIgnoreCase(direction);
    }
}
