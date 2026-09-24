package dsc.domain;

import java.util.List;

/**
 * Immutable instrument output fragment. Nothing here is "cleaned": the numeric
 * arrays are in the instrument's own ordering, {@code badRows} lists rows whose
 * temperature/heat-flow cells could not be parsed (the original text survives
 * in {@link RawRowIssue}), and {@code convention} records the endo/exo sign
 * convention exactly as the instrument header states it.
 */
public record RawData(
        String sourceName,
        String temperatureUnit,
        String heatFlowUnit,
        HeatFlowConvention convention,
        double[] temperature,
        double[] heatFlow,
        List<RawRowIssue> badRows,
        String originalText
) {
    public int size() {
        return temperature.length;
    }

    public record RawRowIssue(int rowIndex, String rawTemperature, String rawHeatFlow, String reason) {
    }
}
