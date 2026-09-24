package org.materials.dsc.domain;

/**
 * One sampled point exactly as imported.  Temperatures are kept in their raw
 * representation and unit until derivation time; normalizedC is populated only
 * by the conversion step and stays null when the value is unconvertible.
 */
public final class RawPoint {
    public final int index;
    public final String rawTemperature;
    public final String tempUnit;
    public final String rawHeatFlow;
    public final String hfUnit;
    public final String rawTime;
    public final String timeUnit;

    public RawPoint(int index, String rawTemperature, String tempUnit,
                    String rawHeatFlow, String hfUnit, String rawTime, String timeUnit) {
        this.index = index;
        this.rawTemperature = rawTemperature;
        this.tempUnit = tempUnit;
        this.rawHeatFlow = rawHeatFlow;
        this.hfUnit = hfUnit;
        this.rawTime = rawTime;
        this.timeUnit = timeUnit;
    }
}
