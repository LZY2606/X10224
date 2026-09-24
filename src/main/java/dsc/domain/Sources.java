package dsc.domain;

import java.util.List;

/** Direct source records: instrument output, mass, scan rate, atmosphere, calibration, tangent pick. */
public final class Sources {
    private Sources() {}

    public record Sample(String id, String code, String material, String createdAt) {}

    /** One heating/cooling segment of one run: the parsed instrument output, raw text retained. */
    public record InstrumentCurve(
            String id,
            String sampleId,
            String runLabel,
            int sequenceNo,                 // position in the sample history (0-based)
            String directionRaw,           // exact text from header
            Direction direction,
            String signConventionRaw,      // exact text from header
            SignConvention signConvention,
            String tempUnitRaw,
            String hfUnitRaw,
            Quant scanRate,                // from the segment header
            String atmosphereId,
            List<DataPoint> points,        // in acquisition order; never resampled
            List<Gap> gaps,                // large/irregular temperature spacing diagnostics
            List<String> diagnostics) {}

    /** Raw acquisition sample. Normalization (units, sign, mass) happens only in the derivation layer. */
    public record DataPoint(int index, String tempRaw, String hfRaw,
                            Double tempC, Double hfMW) {}

    /** A detected irregular (non-uniform) temperature interval between consecutive samples. */
    public record Gap(int afterIndex, double tempDelta, double medianDelta, double ratio) {}

    public record MassVersion(String id, String sampleId, int version, Quant mass,
                              String note, String createdAt, boolean current) {}

    public record Atmosphere(String id, String sampleId, String gasRaw, Quant flow, String note) {}

    /** Temperature calibration offset applied to the reading: T_cal = T_read + offset. */
    public record Calibration(String id, String sampleId, int version,
                              Quant offsetC, String standardRaw, String note, boolean current) {}

    /**
     * User-selected tangent/anchor for a specific segment interval. Kept separately from
     * the instrument output and from computed candidates.
     */
    public record TangentPick(String id, String segmentId, String intervalId,
                              int leftIndex, int rightIndex, String createdAt, String note) {}
}
