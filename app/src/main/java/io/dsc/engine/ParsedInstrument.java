package io.dsc.engine;

import io.dsc.model.Quantity;
import io.dsc.model.ScanDirection;
import io.dsc.model.SignConvention;

import java.util.List;

/** Structured parse of an instrument-output file. Raw rows are retained. */
public record ParsedInstrument(
        String segmentId,
        int cycle,
        ScanDirection direction,       // resolved from header metadata
        String directionRaw,           // original header token
        SignConvention signConvention,
        String signConventionRaw,
        String fileNameHint,
        List<String> headerDiagnostics,
        List<ParsedRow> rows
) {
    public record ParsedRow(
            int index,
            String rawLine,
            String rawTime, String rawTemperature, String rawHeatFlow,
            Quantity time, Quantity temperature, Quantity heatFlow,
            boolean temperatureUnconvertible, boolean heatFlowUnconvertible
    ) {}
}
