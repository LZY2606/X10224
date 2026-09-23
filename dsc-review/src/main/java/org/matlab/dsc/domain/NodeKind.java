package org.matlab.dsc.domain;

/**
 * Derived artifact kinds in the dependency graph.
 */
public enum NodeKind {
    CALIBRATED_SERIES("calibrated_series"),
    SIGNED_SERIES("signed_series"),
    SEGMENT("segment"),
    MASS_NORMALIZED_SERIES("mass_normalized_series"),
    BASELINE("baseline"),
    TRANSITION("transition");

    private final String wire;

    NodeKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static NodeKind fromWire(String wire) {
        for (NodeKind k : values()) {
            if (k.wire.equals(wire)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown node kind: " + wire);
    }
}
