package dsc.model;

/**
 * A domain quantity. Every value carries a unit AND a coordinate basis
 * (e.g. "sample-temperature" vs "furnace-temperature"), so values from
 * different bases are never silently mixed.
 */
public record Quantity(double value, String unit, String basis) {
    @Override public String toString() {
        return value + " " + unit + " @" + basis;
    }
}
