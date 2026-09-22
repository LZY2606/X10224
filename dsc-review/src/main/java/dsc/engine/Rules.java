package dsc.engine;

/**
 * Versioned rule set. Every derived version embeds this string so a replay
 * against an older archive can show which rules produced a number.
 */
public final class Rules {

    public static final String VERSION = "dsc-rules-1.0.0";

    private Rules() {
    }
}
