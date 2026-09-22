package dsc.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * Stable identity factory.
 *
 * <p>Source/decision identities are explicit UUID-like strings created once and
 * embedded in the originating event, so export → delete local DB → import keeps
 * every identity byte-identical. Derived identities are content-addressed from
 * their exact dependency set (rule version + input versions + parameters): the
 * same inputs recompute to the same id, so identity proves "same derivation".
 */
public final class Ids {

    private Ids() {}

    public static String newDecisionId(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }

    /** Content-addressed id for a pure derivation. */
    public static String derived(String kind, String... basisParts) {
        return kind + "_" + sha12(String.join("", basisParts));
    }

    public static String sha12(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
