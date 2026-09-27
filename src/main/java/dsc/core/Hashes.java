package dsc.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable identity helper. IDs are content-addressed so the same source with
 * the same history yields the same ID; identity therefore survives export,
 * database deletion and re-import.
 */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Short, collision-resistant content id with a readable prefix. */
    public static String id(String prefix, String content) {
        return prefix + "_" + sha256(prefix + "|" + content).substring(0, 16);
    }
}
