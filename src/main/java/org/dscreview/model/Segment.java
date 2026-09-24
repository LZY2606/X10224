package org.dscreview.model;

import java.util.List;
import static org.dscreview.model.DomainEnums.ScanDirection;
import static org.dscreview.model.DomainEnums.SignConvention;

/**
 * A raw heating or cooling DSC segment (one ramp in the sample history).
 * Every source field is archived separately with its original representation.
 */
public record Segment(
        String segmentId,
        String label,
        int cycle,
        ScanDirection declaredDirection,
        SignConvention signConvention,
        Quant startTemperature,
        Quant endTemperature,
        Quant scanRate,
        Atmosphere atmosphere,
        Quant heatFlowRange,
        String instrumentModel,
        String originalFileName,
        List<DscPoint> points,
        List<Diagnostic> diagnostics) {

    public record Atmosphere(Quant gas, Quant flowRate, String rawLine) {
        public static Atmosphere of(String gas, double flow) {
            return new Atmosphere(Quant.of(1, gas), Quant.of(flow, "mL/min"), gas + " " + flow + " mL/min");
        }
    }
}
