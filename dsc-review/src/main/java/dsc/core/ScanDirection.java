package dsc.core;

/** Heating/cooling classification, always derived from the data, never the file name. */
public enum ScanDirection {
    HEATING("heating"),
    COOLING("cooling"),
    MIXED("mixed"),
    UNKNOWN("unknown");

    private final String code;
    ScanDirection(String code) { this.code = code; }
    public String code() { return code; }

    /**
     * Classify from temperature-versus-time behaviour. A reversal inside one
     * segment is a cycle-boundary defect, not a silently relabelled segment.
     */
    public static ScanDirection classify(double[] tempC, double[] timeS) {
        int up = 0, down = 0;
        for (int i = 1; i < tempC.length; i++) {
            if (Double.isNaN(tempC[i]) || Double.isNaN(tempC[i - 1])) continue;
            double dt = timeS[i] - timeS[i - 1];
            double dT = tempC[i] - tempC[i - 1];
            if (dt <= 0) continue;
            if (dT > 1e-9) up++;
            else if (dT < -1e-9) down++;
        }
        if (up > 0 && down > 0) return MIXED;
        if (up > 0) return HEATING;
        if (down > 0) return COOLING;
        return UNKNOWN;
    }
}
