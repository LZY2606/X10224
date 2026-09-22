package dsc.model;

public enum ScanMode {
    HEATING("heating"),
    COOLING("cooling"),
    ISOTHERMAL("isothermal"),
    UNKNOWN("unknown");

    private final String token;

    ScanMode(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    public static ScanMode fromToken(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        return switch (raw.trim().toLowerCase()) {
            case "heating", "heat", "加热" -> HEATING;
            case "cooling", "cool", "冷却" -> COOLING;
            case "isothermal", "iso", "恒温" -> ISOTHERMAL;
            default -> UNKNOWN;
        };
    }
}
