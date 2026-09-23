package org.matlab.dsc.domain;

/**
 * Direct (raw) provenance categories. Each kind is archived independently:
 * instrument output, sample mass, scan rate, atmosphere, temperature
 * calibration and the user-selected tangents are never folded into one blob.
 */
public enum SourceKind {
    INSTRUMENT("instrument"),
    MASS("mass"),
    SCAN_RATE("scan_rate"),
    ATMOSPHERE("atmosphere"),
    CALIBRATION("calibration"),
    TANGENT("tangent");

    private final String wire;

    SourceKind(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static SourceKind fromWire(String wire) {
        for (SourceKind k : values()) {
            if (k.wire.equals(wire)) {
                return k;
            }
        }
        throw new IllegalArgumentException("unknown source kind: " + wire);
    }
}
