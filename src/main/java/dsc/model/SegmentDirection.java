package dsc.model;

public enum SegmentDirection {
    HEATING, COOLING;

    public static SegmentDirection fromMetadata(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase();
        if (v.equals("heating") || v.equals("heat") || v.equals("up")) return HEATING;
        if (v.equals("cooling") || v.equals("cool") || v.equals("down")) return COOLING;
        return null;
    }
}
