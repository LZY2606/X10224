package dsc.derive;

/** Versioned rule set. The rule version participates in derived content hashes. */
public final class Rules {
    public static final String VERSION = "dsc-rules-1.0.0";

    public static final double NONUNIFORM_RATIO = 1.5;   // max gap / median gap
    public static final int MIN_INTERVAL_SAMPLES = 3;

    private Rules() {}
}
