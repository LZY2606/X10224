package org.matlab.dsc.ingest;

import org.matlab.dsc.unit.Quantity;
import org.matlab.dsc.unit.Unit;
import org.matlab.dsc.unit.UnitConversionException;

/**
 * Splits a raw cell like "300.15 K" into number + unit and normalises.
 * A value that cannot convert is returned unresolved with the raw string
 * intact; callers attach UNCONVERTIBLE_VALUE_KEPT diagnostics.
 */
public record RawValue(String raw, Double value, Unit unit, String error) {

    public static RawValue parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new RawValue(raw, null, null, "empty cell");
        }
        String trimmed = raw.trim();
        int sp = firstUnitStart(trimmed);
        String numPart = (sp < 0 ? trimmed : trimmed.substring(0, sp)).trim();
        String unitPart = (sp < 0 ? "" : trimmed.substring(sp)).trim();
        try {
            double v = Double.parseDouble(numPart);
            if (Double.isNaN(v) || Double.isInfinite(v)) {
                return new RawValue(raw, null, null, "non-finite numeric value");
            }
            if (unitPart.isEmpty()) {
                return new RawValue(raw, v, null, "missing unit");
            }
            Unit u = Unit.parse(unitPart);
            return new RawValue(raw, v, u, null);
        } catch (NumberFormatException e) {
            return new RawValue(raw, null, null, "not a number: " + numPart);
        } catch (UnitConversionException e) {
            try {
                double v = Double.parseDouble(numPart);
                return new RawValue(raw, v, null, e.getMessage());
            } catch (NumberFormatException nfe) {
                return new RawValue(raw, null, null, e.getMessage());
            }
        }
    }

    public boolean resolved() {
        return value != null && unit != null && error == null;
    }

    public Quantity quantity() {
        if (!resolved()) {
            throw new UnitConversionException("unresolved raw value: " + raw);
        }
        return new Quantity(value, unit);
    }

    private static int firstUnitStart(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c) || c == '.' || c == '-' || c == '+' || c == 'e' || c == 'E'
                    || Character.isWhitespace(c)) {
                continue;
            }
            // walk back over whitespace
            int j = i;
            while (j > 0 && Character.isWhitespace(s.charAt(j - 1))) {
                j--;
            }
            return j;
        }
        return -1;
    }
}
