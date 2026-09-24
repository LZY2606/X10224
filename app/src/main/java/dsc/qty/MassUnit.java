package dsc.qty;

import java.util.Locale;

public enum MassUnit {
    MG(0.001),
    G(1.0),
    KG(1000.0),
    UG(0.000001);

    private final double factorToGram;

    MassUnit(double factorToGram) {
        this.factorToGram = factorToGram;
    }

    public double toGrams(double value) {
        return value * factorToGram;
    }

    public double fromGrams(double grams) {
        return grams / factorToGram;
    }

    public String symbol() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MassUnit fromSymbol(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("µg") || t.equals("ug")) {
            return UG;
        }
        for (MassUnit u : values()) {
            if (u.name().equalsIgnoreCase(t) || u.symbol().equals(t)) {
                return u;
            }
        }
        throw new UnitException("Unknown mass unit: " + s);
    }
}
