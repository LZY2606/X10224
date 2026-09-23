package dsc.model;

import java.util.Locale;

/** Stable identity helpers. Ids survive export / delete / re-import. */
public final class Ids {

    private Ids() {}

    public static String source(String study, String code) {
        return "src-" + study + "-" + code;
    }

    public static String segment(int order) {
        return String.format(Locale.ROOT, "seg-%02d", order);
    }

    public static String candidate(int no) {
        return String.format(Locale.ROOT, "bl-%03d", no);
    }

    public static String transition(int no) {
        return String.format(Locale.ROOT, "tr-%03d", no);
    }

    public static String window(String candidateId) {
        return "win-" + candidateId;
    }
}
