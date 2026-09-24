package org.materials.dsc.domain;

public enum ThermalEventType {
    GLASS_TRANSITION("glass_transition"),
    CRYSTALLIZATION("crystallization"),
    MELTING("melting"),
    /** Two transitions whose windows overlap in temperature. */
    OVERLAPPING("overlapping"),
    UNKNOWN("unknown");

    private final String code;

    ThermalEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
