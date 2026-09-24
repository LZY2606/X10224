package io.dsc.model;

/**
 * Version of the calculation rule set. Every derivation stamps the rule version
 * so historical views can answer "which rule produced this?".
 */
public final class RuleVersion {
    private RuleVersion() {}
    public static final String CURRENT = "dsc-rules-2026.1";
}
