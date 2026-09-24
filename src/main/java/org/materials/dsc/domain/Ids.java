package org.materials.dsc.domain;

/**
 * Stable identity helpers.  Identifiers are meaningful, content-stable and
 * survive export -> delete -> import.  They are never row ids.
 */
public final class Ids {

    private Ids() {
    }

    public static String sample(String code) {
        return "sample:" + slug(code);
    }

    public static String history(String sampleId, int ordinal) {
        return sampleId + "/history#" + ordinal;
    }

    public static String segment(String historyId, String ordinal) {
        return historyId + "/seg:" + slug(ordinal);
    }

    public static String source(String segmentId, String kind, String name) {
        return segmentId + "/" + kind + ":" + slug(name);
    }

    public static String event(String segmentId, String name) {
        return segmentId + "/event:" + slug(name);
    }

    public static String baseline(String eventId, String method) {
        return eventId + "/baseline:" + slug(method);
    }

    public static String integration(String baselineId) {
        return baselineId + "/integration";
    }

    public static String decision(String targetId, int ordinal) {
        return targetId + "/decision#" + ordinal;
    }

    public static String slug(String s) {
        if (s == null) {
            return "x";
        }
        String out = s.trim().toLowerCase().replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-").replaceAll("^-|-$", "");
        return out.isEmpty() ? "x" : out;
    }
}
