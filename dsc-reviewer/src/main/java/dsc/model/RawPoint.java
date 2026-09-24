package dsc.model;

import java.util.Locale;

/**
 * One imported instrument row exactly as expressed by the source file.
 * Nothing here is converted: temperature keeps the file unit string and the original token,
 * heat flow likewise. The derivation layer performs unit conversion and may leave a cell
 * unconverted ({@link Quant#isValid()} == false) with a diagnostic attached.
 */
public record RawPoint(int rowIndex, String tempToken, String tempUnit,
                       String hfToken, String hfUnit, String timeToken, String timeUnit) {

    public String display() {
        return String.format(Locale.ROOT, "#%d T=%s%s HF=%s%s",
                rowIndex, tempToken, tempUnit == null ? "" : " " + tempUnit,
                hfToken, hfUnit == null ? "" : " " + hfUnit);
    }
}
