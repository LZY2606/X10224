package org.matlab.dsc.domain;

/**
 * Versioned rule set. Every derived node records which rule version produced
 * it, so an old event can always be revisited with the exact rule basis that
 * was current then.
 */
public record RuleVersion(String id, String name, String semver) {
    public static final RuleVersion CURRENT = new RuleVersion("rules:dsc-core", "dsc-core", "1.0.0");

    public static RuleVersion of(String id) {
        if (CURRENT.id().equals(id)) {
            return CURRENT;
        }
        return new RuleVersion(id, id, "0.0-legacy");
    }
}
