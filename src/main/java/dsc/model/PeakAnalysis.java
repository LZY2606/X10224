package dsc.model;

import java.util.List;

/**
 * Integration result for one interval under one baseline. Every number
 * carries its unit; error sources are listed explicitly so the review page
 * can show where the enthalpy uncertainty comes from.
 */
public final class PeakAnalysis {
    public final String baselineId;
    public final Quantity onsetTemp;
    public final Quantity peakTemp;
    public final Quantity endsetTemp;
    public final Quantity peakSignal;
    /** Integrated area between curve and baseline, J per gram. */
    public final Quantity enthalpy;
    /** +1 when the event is endothermic in canonical orientation, -1 exothermic, 0 unknown. */
    public final int endoSign;
    public final List<String> errorSources;
    public final List<Diagnostic> diagnostics;

    public PeakAnalysis(String baselineId, Quantity onsetTemp, Quantity peakTemp,
                        Quantity endsetTemp, Quantity peakSignal, Quantity enthalpy,
                        int endoSign, List<String> errorSources, List<Diagnostic> diagnostics) {
        this.baselineId = baselineId;
        this.onsetTemp = onsetTemp;
        this.peakTemp = peakTemp;
        this.endsetTemp = endsetTemp;
        this.peakSignal = peakSignal;
        this.enthalpy = enthalpy;
        this.endoSign = endoSign;
        this.errorSources = errorSources == null ? List.of() : errorSources;
        this.diagnostics = diagnostics == null ? List.of() : diagnostics;
    }
}
