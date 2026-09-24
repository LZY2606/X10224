package org.materials.dsc.domain;

/** 规则版本。旧事件回放时恢复“当时依赖版本+当时结论”，而非用最新规则覆盖历史。 */
public final class RuleVersions {
    public static final String CURRENT = "rules-1.0.0";
    public static final String FIXTURE_RULES = "rules-1.0.0";

    private RuleVersions() {
    }
}
