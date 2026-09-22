package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * Derived thermal characterization of one interval against one accepted baseline:
 * onset / peak / endset temperatures and integrated enthalpy, with an error budget.
 *
 * The result is per segment-direction: heating and cooling are never mixed.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ThermalResult {
    public String id;
    public String intervalId;
    public String segmentId;
    public String baselineId;
    public String eventType;
    public ScanDirection direction;

    public double onsetT;
    public double peakT;
    public double endsetT;
    public double peakHeight;
    public double enthalpy;
    public String sign;
    public double enthalpyAbs;
    public double integratedSamples;
    public double combinedUncertainty;
    public boolean nonUniformTemperature = false;
    public boolean overlapping = false;
    public final List<ErrorSource> errorSources = new ArrayList<>();
    public final List<String> diagnostics = new ArrayList<>();

    public String inputsFingerprint;
    public String rulesetVersion;

    public ThermalResult() {
    }
}
