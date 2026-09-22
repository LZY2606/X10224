package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * One legitimate baseline for an interval. Multiple reasonable candidates coexist and
 * keep their differences; acceptance only moves a pointer, it never deletes alternatives.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class BaselineCandidate {
    public String id;
    public String intervalId;
    public String method;
    public String methodLabel;
    public String tangentSourceId;
    public final List<double[]> curve = new ArrayList<>();
    public double residualRms;
    public double residualMax;
    public boolean usable = true;
    public String unusableReason;
    public final List<String> warnings = new ArrayList<>();
    /** Inputs + ruleset fingerprint this candidate was derived from. */
    public String inputsFingerprint;
    public String rulesetVersion;

    public BaselineCandidate() {
    }
}
