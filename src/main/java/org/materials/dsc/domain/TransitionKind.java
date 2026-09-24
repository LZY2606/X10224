package org.materials.dsc.domain;

public enum TransitionKind {
    GLASS, CRYSTALLIZATION, MELTING, OTHER;

    public String label() {
        return switch (this) {
            case GLASS -> "glass-transition";
            case CRYSTALLIZATION -> "crystallization";
            case MELTING -> "melting";
            case OTHER -> "other";
        };
    }

    /** 玻璃化报告 ΔCp；结晶/熔融报告焓。 */
    public boolean reportsEnthalpy() {
        return this != GLASS;
    }
}
