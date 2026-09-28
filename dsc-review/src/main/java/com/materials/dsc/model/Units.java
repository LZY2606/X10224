package com.materials.dsc.model;

import java.util.Set;

/**
 * Unit / coordinate parsing and normalisation.  Normalisation happens only in
 * the derived layer; the raw token is always retained.
 */
public final class Units {
    public static final String CELSIUS = "degC";
    public static final String KELVIN = "K";
    public static final String MG = "mg";
    public static final String G = "g";
    public static final String SECOND = "s";
    public static final String MINUTE = "min";
    public static final String MW = "mW";
    public static final String W = "W";
    public static final String J_PER_G = "J/g";
    public static final String J_PER_GK = "J/(g*K)";

    private Units() {
    }

    private static Double number(String raw) {
        try {
            return Double.parseDouble(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String normaliseUnit(String u) {
        if (u == null) {
            return null;
        }
        return u.trim().replace(" ", "")
                .replace("°C", CELSIUS).replace("oC", CELSIUS).replace("℃", CELSIUS)
                .replace("℃", CELSIUS)
                .replace("mg", MG);
    }

    public static Qty parse(String raw, Set<String> allowedUnits, String quantityName) {
        if (raw == null) {
            return Qty.unconvertible(null, null, quantityName + ": missing value");
        }
        String token = raw.trim();
        int sp = -1;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (Character.isLetter(c) || c == '°' || c == '℃') {
                sp = i;
                break;
            }
        }
        String numToken = sp < 0 ? token : token.substring(0, sp).trim();
        String unitToken = sp < 0 ? "" : normaliseUnit(token.substring(sp).trim());
        Double v = number(numToken);
        if (v == null) {
            return Qty.unconvertible(raw, unitToken.isEmpty() ? null : unitToken,
                    quantityName + ": cannot parse number '" + numToken + "'");
        }
        if (!allowedUnits.contains(unitToken)) {
            return Qty.unconvertible(raw, unitToken.isEmpty() ? null : unitToken,
                    quantityName + ": unsupported/ambiguous unit '" + token.substring(sp < 0 ? 0 : sp)
                            + "', expected one of " + allowedUnits);
        }
        double norm = v;
        String normUnit = unitToken;
        switch (unitToken) {
            case KELVIN:
                norm = v - 273.15;
                normUnit = CELSIUS;
                break;
            case MG:
                norm = v / 1000.0;
                normUnit = G;
                break;
            case G:
            case CELSIUS:
                break;
            case MINUTE:
                norm = v * 60.0;
                normUnit = SECOND;
                break;
            case SECOND:
                break;
            case W:
                norm = v * 1000.0;
                normUnit = MW;
                break;
            case MW:
                break;
            default:
                break;
        }
        return Qty.parsed(raw, norm, normUnit);
    }

    public static final Set<String> TEMPERATURE = Set.of(CELSIUS, KELVIN);
    public static final Set<String> MASS = Set.of(MG, G);
    public static final Set<String> TIME = Set.of(SECOND, MINUTE);
    public static final Set<String> RATE = Set.of(MW, W);
}
