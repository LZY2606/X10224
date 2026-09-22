package dsc.domain;

/**
 * Scan direction comes from explicit metadata, never from a file name.
 * Heating and cooling segments must never be mixed into one integration.
 */
public enum ScanDirection {
    HEATING("heating", "加热"),
    COOLING("cooling", "冷却");

    public final String code;
    public final String label;

    ScanDirection(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ScanDirection fromCode(String code) {
        for (ScanDirection d : values()) {
            if (d.code.equals(code)) {
                return d;
            }
        }
        throw new IllegalArgumentException("unknown scan direction: " + code);
    }
}
