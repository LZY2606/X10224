package dsc.model;

public enum TransitionKind {
    GLASS("glass"),
    CRYSTALLIZATION("crystallization"),
    MELT("melt"),
    GENERIC("generic");

    private final String code;
    TransitionKind(String code) { this.code = code; }
    public String code() { return code; }

    public static TransitionKind parse(String s) {
        return switch (s == null ? "" : s.trim().toLowerCase()) {
            case "glass", "tg" -> GLASS;
            case "crystallization", "cryst", "crystal" -> CRYSTALLIZATION;
            case "melt", "melting", "fusion" -> MELT;
            default -> GENERIC;
        };
    }
}
