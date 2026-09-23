package org.matlab.dsc.domain;

public enum TransitionKind {
    GLASS("glass"),
    CRYSTALLIZATION("crystallization"),
    MELTING("melting"),
    UNKNOWN("unknown");

    private final String wire;

    TransitionKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static TransitionKind fromWire(String wire) {
        for (TransitionKind k : values()) {
            if (k.wire.equals(wire)) {
                return k;
            }
        }
        return UNKNOWN;
    }
}
