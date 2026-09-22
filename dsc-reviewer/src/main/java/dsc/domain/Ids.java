package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Deterministic stable identities so export/re-import keeps the same id and order. */
public final class Ids {
    private Ids() {
    }

    public static String seg(String sampleSlug, int ordinal) {
        return "seg/" + slug(sampleSlug) + "/" + ordinal;
    }

    public static String source(String scope, Types.SourceKind kind) {
        return "src/" + scope + "/" + kind.name().toLowerCase(Locale.ROOT);
    }

    public static String series(String segmentId) {
        return "series/" + segmentId;
    }

    public static String seriesHypothesis(String segmentId, String hypothesis) {
        return "series/" + segmentId + "/" + hypothesis;
    }

    public static String window(String segmentId, String slug) {
        return "win/" + segmentId + "/" + slug;
    }

    public static String baseline(String windowId, Types.BaselineMethod method) {
        return "bl/" + windowId + "/" + method.name().toLowerCase(Locale.ROOT);
    }

    public static String transition(String windowId, String peakSlug) {
        return "tr/" + windowId + "/" + peakSlug;
    }

    public static String report(String windowId) {
        return "report/" + windowId;
    }

    public static String event(long seq, String contentHash) {
        return "evt/" + seq + "/" + contentHash.substring(0, 10);
    }

    public static String slug(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
