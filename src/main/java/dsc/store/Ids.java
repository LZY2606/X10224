package dsc.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable identity helpers. IDs are deterministic content hashes so an
 * exported archive re-imported after deleting the local database keeps the
 * same artifact ids, ordering and provenance links. */
public final class Ids {
    private Ids() {
    }

    public static String sha256(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(56);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String shortId(String prefix, String content) {
        return prefix + "_" + sha256(content).substring(0, 16);
    }
}
