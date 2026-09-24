package dsc.domain;

/**
 * One instrument sample. Temperatures are stored as reported (tValue/tUnit)
 * and only converted in derivations. index preserves the original order.
 */
public record SamplePoint(int index, double tValue, String tUnit,
                          double hfValue, String hfUnit, double timeSec) {

    /** Temperature converted to kelvin point value (offset, not linear-only). */
    public double tempK() {
        if (UnitTables.isTemperature(tUnit)) {
            return switch (tUnit) {
                case "K" -> tValue;
                case "C", "degC", "°C" -> tValue + 273.15;
                default -> tValue;
            };
        }
        return Double.NaN;
    }

    public double heatFlowMilliWatt() {
        Quantity q = new Quantity(hfValue, hfUnit, null).to("mW");
        return q == null ? Double.NaN : q.value();
    }
}
