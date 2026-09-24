package org.dscreview.model;

import java.util.List;
import static org.dscreview.model.DomainEnums.EventType;
import static org.dscreview.model.DomainEnums.ThermalSense;

/**
 * A glass / crystallization / melting event derived from a baseline candidate.
 * All displayed values carry units or a coordinate basis.
 */
public record ThermalEvent(
        String eventResultId,
        String candidateId,
        String segmentId,
        EventType type,
        ThermalSense sense,
        double onsetDegC,
        double peakDegC,
        double endsetDegC,
        double peakHeightMw,
        double enthalpyMj,
        Quant enthalpyPerMass,
        double uncertaintyMj,
        List<String> errorSources,
        List<Diagnostic> diagnostics) {

    public ThermalEvent withEnthalpyPerMass(Quant q) {
        return new ThermalEvent(eventResultId, candidateId, segmentId, type, sense,
                onsetDegC, peakDegC, endsetDegC, peakHeightMw, enthalpyMj, q,
                uncertaintyMj, errorSources, diagnostics);
    }
    public ThermalEvent withErrorSources(List<String> srcs) {
        return new ThermalEvent(eventResultId, candidateId, segmentId, type, sense,
                onsetDegC, peakDegC, endsetDegC, peakHeightMw, enthalpyMj, enthalpyPerMass,
                uncertaintyMj, srcs, diagnostics);
    }
}
