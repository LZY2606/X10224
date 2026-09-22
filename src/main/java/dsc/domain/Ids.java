package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Stable identities.
 *
 * <p>Content-derived ids are deterministic: the same rule version, inputs and parameters always
 * produce the same id, so an exported/deleted/re-imported archive keeps every displayed value's
 * identity. User-authored objects (intervals, decisions) use random ids.
 */
public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String contentId(String prefix, String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0x1f);
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return prefix + "_" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static String randomId(String prefix) {
        byte[] bytes = new byte[6];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return prefix + "_" + hex;
    }
}
