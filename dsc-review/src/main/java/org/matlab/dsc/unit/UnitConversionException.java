package org.matlab.dsc.unit;

/**
 * Thrown when a raw value cannot be converted into the canonical frame.
 * Callers must keep the original representation in place and attach a
 * diagnostic rather than dropping the value for plotting.
 */
public class UnitConversionException extends RuntimeException {
    public UnitConversionException(String message) {
        super(message);
    }
}
