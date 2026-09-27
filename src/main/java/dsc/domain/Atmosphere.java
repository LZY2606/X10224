package dsc.domain;

import java.util.Objects;

/** Furnace atmosphere as recorded, e.g. N2 at 50 mL/min. */
public final class Atmosphere {
    private final String gas;
    private final Qty flowRate;
    private final String note;

    public Atmosphere(String gas, Qty flowRate, String note) {
        this.gas = gas;
        this.flowRate = flowRate;
        this.note = note;
    }

    public String gas() {
        return gas;
    }

    public Qty flowRate() {
        return flowRate;
    }

    public String note() {
        return note;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder(gas == null ? "?" : gas);
        if (flowRate != null) {
            sb.append(" @ ").append(flowRate.format());
        }
        if (note != null && !note.isBlank()) {
            sb.append(" (").append(note).append(")");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Atmosphere)) return false;
        Atmosphere that = (Atmosphere) o;
        return Objects.equals(gas, that.gas) && Objects.equals(flowRate, that.flowRate)
                && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gas, flowRate, note);
    }
}
