package io.dsc.model;

import java.util.List;

/**
 * One heating or cooling leg of a sample history. A sample history may carry
 * several segments (cycle switching); heating and cooling legs are never mixed
 * in one derivation interval.
 */
public record Segment(
        StableId id,
        String caseId,
        int cycle,
        ScanDirection direction,     // from instrument header, not file name
        SignConvention signConvention,
        String headerDirectionRaw,   // original header token retained
        String fileNameHint,         // original file name retained (advisory only)
        Quantity startTemperature,
        Quantity endTemperature,
        List<Sample> samples,        // raw points, original representation retained
        int instrumentOutputVersion
) {
    public int pointCount() { return samples.size(); }

    public static record Sample(
            int index,
            String rawTime, String rawTemperature, String rawHeatFlow,
            double tK,        // normalised temperature (K), derivation layer
            double s,         // normalised time (s)
            Double hfW,       // normalised heat flow (W), null if unconvertible
            boolean hfUnconvertible,
            boolean temperatureUnconvertible) {
        public boolean plottable() { return hfW != null; }
    }
}
