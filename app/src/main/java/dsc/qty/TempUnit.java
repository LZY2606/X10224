package dsc.qty;

/** Temperature coordinate basis. */
public enum TempUnit {
    C("°C", 0.0),
    K("K", 273.15),
    F("°F", -17.77777777777778);

    private final String symbol;
    private final double offsetToC;

    TempUnit(String symbol, double offsetToC) {
        this.symbol = symbol;
        this.offsetToC = offsetToC;
    }

    public String symbol() {
        return symbol;
    }

    public double toCelsius(double value) {
        switch (this) {
            case C:
                return value;
            case K:
                return value - 273.15;
            case F:
                return (value - 32.0) * 5.0 / 9.0;
            default:
                throw new UnitException("Unknown temperature unit: " + this);
        }
    }

    public double fromCelsius(double valueC) {
        switch (this) {
            case C:
                return valueC;
            case K:
                return valueC + 273.15;
            case F:
                return valueC * 9.0 / 5.0 + 32.0;
            default:
                throw new UnitException("Unknown temperature unit: " + this);
        }
    }

    public double offsetToC() {
        return offsetToC;
    }

    public static TempUnit fromSymbol(String s) {
        String t = s.trim();
        for (TempUnit u : values()) {
            if (u.symbol.equals(t) || u.name().equalsIgnoreCase(t)) {
                return u;
            }
        }
        throw new UnitException("Unknown temperature unit: " + s);
    }
}
