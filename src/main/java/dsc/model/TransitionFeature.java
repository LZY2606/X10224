package dsc.model;

import java.util.List;
import java.util.Map;

/**
 * Onset / peak / endset and enthalpy derived from one baseline candidate inside an interval.
 * Every displayed value carries a stable identity ({@link #id}) and a list of error sources.
 */
public final class TransitionFeature {
    public final String id;
    public final String baselineId;
    public final Types.TransitionKind kind;
    /** Signed under the endo-up convention; positive = endothermic (e.g. melting). */
    public final double onsetK;
    public final double peakK;
    public final double endsetK;
    /** Specific enthalpy in J/kg, endo positive; NaN when mass normalisation is unavailable. */
    public final double specificEnthalpyJPerKg;
    /** Raw integrated signal in J (independent of mass). */
    public final double integratedEnergyJ;
    public final List<String> errorSources;
    /** Display values with units/coordinates, keyed by stable component name. */
    public final Map<String, String> annotated;

    public TransitionFeature(String id, String baselineId, Types.TransitionKind kind,
                             double onsetK, double peakK, double endsetK,
                             double integratedEnergyJ, double specificEnthalpyJPerKg,
                             List<String> errorSources, Map<String, String> annotated) {
        this.id = id;
        this.baselineId = baselineId;
        this.kind = kind;
        this.onsetK = onsetK;
        this.peakK = peakK;
        this.endsetK = endsetK;
        this.integratedEnergyJ = integratedEnergyJ;
        this.specificEnthalpyJPerKg = specificEnthalpyJPerKg;
        this.errorSources = errorSources;
        this.annotated = annotated;
    }
}
