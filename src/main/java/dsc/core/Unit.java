package dsc.core;

/**
 * Physical units with explicit dimension and conversion factors.
 * Values that cannot be converted stay in their original unit and carry a
 * diagnostic instead of being silently dropped for plotting.
 */
public enum Unit {
    CELSIUS("°C", Dim.TEMPERATURE, Double.NaN, 273.15, 0),
    KELVIN("K", Dim.TEMPERATURE, Double.NaN, 0, 1),
    MILLIWATT("mW", Dim.HEAT_FLOW, 1.0, 0, 1),
    WATT("W", Dim.HEAT_FLOW, 1000.0, 0, 1),
    MICROVOLT("µV", Dim.SENSOR_RAW, Double.NaN, 0, 1),
    MG("mg", Dim.MASS, 1.0, 0, 1),
    G("g", Dim.MASS, 1000.0, 0, 1),
    K_PER_MIN("K/min", Dim.RATE, 1.0, 0, 1),
    C_PER_MIN("°C/min", Dim.RATE, 1.0, 0, 1),
    K_PER_S("K/s", Dim.RATE, 60.0, 0, 1),
    SECOND("s", Dim.TIME, 1.0, 0, 1),
    MIN("min", Dim.TIME, 60.0, 0, 1),
    MILLIJOULE("mJ", Dim.ENERGY, 1.0, 0, 1),
    JOULE("J", Dim.ENERGY, 1000.0, 0, 1),
    J_PER_G("J/g", Dim.SPECIFIC_ENERGY, 1.0, 0, 1),
    UNIT("1", Dim.DIMENSIONLESS, 1.0, 0, 1);

    public enum Dim { TEMPERATURE, HEAT_FLOW, SENSOR_RAW, MASS, RATE, TIME, ENERGY, SPECIFIC_ENERGY, DIMENSIONLESS }

    public final String symbol;
    public final Dim dim;
    private final double factor;   // value * factor + offset -> canonical
    private final double offset;
    private final double canonicalFactor;

    Unit(String symbol, Dim dim, double factor, double offset, double canonicalFactor) {
        this.symbol = symbol;
        this.dim = dim;
        this.factor = factor;
        this.offset = offset;
        this.canonicalFactor = canonicalFactor;
    }

    public double toCanonical(double v) { return v * factor + offset; }
    public double fromCanonical(double c) { return (c - offset) / factor; }

    public double convert(double v, Unit target) {
        if (this == target) return v;
        if (this.dim != target.dim) {
            throw new IllegalArgumentException(
                    "incompatible units: " + symbol + " (" + dim + ") -> " + target.symbol + " (" + target.dim + ")");
        }
        return target.fromCanonical(this.toCanonical(v));
    }

    public boolean convertibleTo(Unit target) {
        return this == target || this.dim == target.dim;
    }

    @Override public String toString() { return symbol; }
}
