package dsc.store;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Stable identity policy.
 * - Imported rows keep the identity present in the case pack (import-as-is),
 *   so delete/re-import preserves provenance.
 * - Locally created rows get prefixed, human-recognisable ids that are also
 *   content-stable when derived from the same inputs/rule version.
 */
public final class Ids {
    private Ids() {}

    public static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update((p == null ? "" : p).getBytes());
                md.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String baseline(String transitionId, String method, String derivedVersionId) {
        return "bl_" + hash(transitionId, method, derivedVersionId);
    }

    public static String analysis(String baselineId, String method) {
        return "an_" + hash(baselineId, method);
    }

    public static String derived(String scopeKey, int number) {
        return "dv_" + hash(scopeKey, Integer.toString(number));
    }

    public static String event(long seq, String type) {
        return "ev_" + hash(Long.toString(seq), type);
    }
}
