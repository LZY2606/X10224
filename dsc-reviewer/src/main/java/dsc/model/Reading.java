package dsc.model;

import dsc.qty.Qty;

/**
 * One sampled line of an instrument output. Each axis keeps its own quantity so
 * an unparseable cell stays in place with a diagnostic instead of dropping the row.
 */
public record Reading(Qty time, Qty temperature, Qty heatFlow, String sourceFile, int sourceLine) {
    public boolean fullyUsable() {
        return time.usable() && temperature.usable() && heatFlow.usable();
    }
}
