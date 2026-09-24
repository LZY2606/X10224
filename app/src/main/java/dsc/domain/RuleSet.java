package dsc.domain;

/**
 * Versioned rule parameters (the "哪版规则"). Revising a parameter appends a new version and
 * invalidates only derivations whose dependency graph reaches it.
 */
public class RuleSet {
    public int version;
    public boolean current;
    public double smoothingWindowC;   // °C window for derivative smoothing
    public double peakProminenceWg;   // W/g minimum prominence
    public double glassStepMinWg;     // W/g minimum step height for Tg
    public String signPolicy;         // human-readable policy statement
    public String createdBy;
    public long createdAt;
    public String note;
}
