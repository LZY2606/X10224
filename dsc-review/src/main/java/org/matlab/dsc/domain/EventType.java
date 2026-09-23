package org.matlab.dsc.domain;

/**
 * Event log categories. REJECTED attempts are first-class events: a rejected
 * interval or an unadopted candidate must leave an auditable trail and remain
 * visible as an affected object.
 */
public enum EventType {
    INGEST("ingest"),
    DERIVE("derive"),
    DECIDE("decide"),
    REJECT("reject"),
    REVISE("revise"),
    CONFLICT("conflict");

    private final String wire;

    EventType(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static EventType fromWire(String wire) {
        for (EventType t : values()) {
            if (t.wire.equals(wire)) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown event type: " + wire);
    }
}
