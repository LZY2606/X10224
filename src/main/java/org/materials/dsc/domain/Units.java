package org.materials.dsc.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * 单位与坐标基准。所有领域量都必须携带单位；导入层保留原始表示，
 * 只有派生层才把数值规范化到规范单位。
 */
public enum Units {
    CELSIUS("°C", "temperature", 1.0, 0.0),
    KELVIN("K", "temperature", 1.0, -273.15),
    MILLIWATT("mW", "heatflow", 1.0, 0.0),
    WATT("W", "heatflow", 1000.0, 0.0),
    GRAM("g", "mass", 1.0, 0.0),
    MILLIGRAM("mg", "mass", 0.001, 0.0),
    KILOGRAM("kg", "mass", 1000.0, 0.0),
    JOULE("J", "energy", 1.0, 0.0),
    MILLIJOULE("mJ", "energy", 0.001, 0.0),
    JOULE_PER_GRAM("J/g", "specific-energy", 1.0, 0.0),
    KJ_PER_KG("kJ/kg", "specific-energy", 1.0, 0.0),
    C_PER_MIN("°C/min", "rate", 1.0, 0.0),
    K_PER_MIN("K/min", "rate", 1.0, 0.0),
    SECOND("s", "time", 1.0, 0.0),
    MINUTE("min", "time", 60.0, 0.0),
    DIMENSIONLESS("", "dimensionless", 1.0, 0.0);

    private final String symbol;
    private final String dimension;
    private final double factor;
    private final double offset;

    Units(String symbol, String dimension, double factor, double offset) {
        this.symbol = symbol;
        this.dimension = dimension;
        this.factor = factor;
        this.offset = offset;
    }

    public String symbol() {
        return symbol;
    }

    public String dimension() {
        return dimension;
    }

    public double toCanonical(double value) {
        return value * factor + offset;
    }

    public double fromCanonical(double canonical) {
        return (canonical - offset) / factor;
    }

    public static Optional<Units> fromToken(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String t = token.trim().replace("degC", "°C").replace("C", "°C")
                .replace("mW/mg", "J/g").replace("W/g", "kJ/kg");
        for (Units u : values()) {
            if (!u.symbol.isEmpty() && (u.symbol.equals(t) || u.name().equalsIgnoreCase(token.trim()))) {
                return Optional.of(u);
            }
            if (u.symbol.isEmpty() && t.isEmpty()) {
                return Optional.of(u);
            }
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "c", "°c", "degc", "celsius" -> Optional.of(CELSIUS);
            case "k", "kelvin" -> Optional.of(KELVIN);
            case "mw" -> Optional.of(MILLIWATT);
            case "w" -> Optional.of(WATT);
            case "g" -> Optional.of(GRAM);
            case "mg" -> Optional.of(MILLIGRAM);
            case "kg" -> Optional.of(KILOGRAM);
            case "j" -> Optional.of(JOULE);
            case "mj" -> Optional.of(MILLIJOULE);
            case "j/g" -> Optional.of(JOULE_PER_GRAM);
            case "kj/kg" -> Optional.of(KJ_PER_KG);
            default -> Optional.empty();
        };
    }
}
