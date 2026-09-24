package dsc.domain;

/**
 * Heating vs cooling is derived from the actual temperature progression in the
 * data — never guessed from the file name.
 */
public enum ScanDirection {
    HEATING,
    COOLING,
    /** Strictly constant or empty progression. */
    ISOTHERMAL,
    /** Direction reverses inside the fragment: the segment must be split before analysis. */
    NON_MONOTONIC;

    public static ScanDirection fromProgression(double[] t) {
        if (t == null || t.length < 2) return ISOTHERMAL;
        int up = 0;
        int down = 0;
        for (int i = 1; i < t.length; i++) {
            int cmp = Double.compare(t[i], t[i - 1]);
            if (cmp > 0) up++;
            else if (cmp < 0) down++;
        }
        if (up > 0 && down > 0) return NON_MONOTONIC;
        if (up > 0) return HEATING;
        if (down > 0) return COOLING;
        return ISOTHERMAL;
    }
}
