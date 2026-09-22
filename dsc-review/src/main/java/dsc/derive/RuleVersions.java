package dsc.derive;

/**
 * Version of the rules used to produce a derivation. Bumped when the numerical
 * semantics change, so old results remain explainable ("which version of the
 * rules caused this difference"). Old code paths are never reused silently:
 * the reducer records the rule version on each derivation and the UI shows it.
 */
public final class RuleVersions {
    public static final String BASELINE_RULES = "baseline-1.0.0";
    public static final String FEATURE_RULES = "features-1.0.0";

    private RuleVersions() {}
}
