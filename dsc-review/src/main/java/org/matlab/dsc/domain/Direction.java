package org.matlab.dsc.domain;

/**
 * Temperature direction of a scan segment. Heating and cooling segments must
 * never be mixed inside one transition integration.
 */
public enum Direction {
    HEATING("heat", +1),
    COOLING("cool", -1);

    private final String wire;
    private final int sign;

    Direction(String wire, int sign) {
        this.wire = wire;
        this.sign = sign;
    }

    public String wire() {
        return wire;
    }

    public int sign() {
        return sign;
    }

    public Direction opposite() {
        return this == HEATING ? COOLING : HEATING;
    }

    public static Direction fromWire(String wire) {
        for (Direction d : values()) {
            if (d.wire.equals(wire)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown direction: " + wire);
    }
}
