package io.dsc.model;

/**
 * Segment thermal direction. Direction MUST be read from the instrument output
 * metadata (header); it must never be guessed from a file name. A file named
 * "...heat..." whose header says cooling is a diagnostic, not a heating run.
 */
public enum ScanDirection {
    HEATING("heating"),
    COOLING("cooling"),
    ISOTHERMAL("isothermal");

    private final String code;
    ScanDirection(String code) { this.code = code; }
    public String code() { return code; }

    public static ScanDirection fromCode(String code) {
        for (ScanDirection d : values()) if (d.code.equalsIgnoreCase(code)) return d;
        throw new IllegalArgumentException("unknown scan direction: " + code);
    }
}
