package dsc.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * Stable identifiers.
 *
 * <p>Source events and decisions get random ULID-like ids. Derived events get
 * deterministic ids: hash(kind + canonical inputs), so re-importing the same
 * case pack yields the same ids, order and provenance.
 */
public final class Ids {
    private static final char[] CROCK = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {
    }

    public static String random(String prefix) {
        long time = Instant.now().toEpochMilli();
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);
        StringBuilder sb = new StringBuilder(prefix).append('_').append(encodeTime(time));
        for (byte b : rnd) {
            sb.append(CROCK[(b & 0xFF) % 32]);
        }
        return sb.toString();
    }

    private static String encodeTime(long time) {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 9; i >= 0; i--) {
            sb.insert(0, CROCK[(int) ((time >>> (5 * i)) & 0x1F)]);
        }
        return sb.toString();
    }

    public static String deterministic(String prefix, String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                md.update((byte) 0);
                md.update(p.getBytes(StandardCharsets.UTF_8));
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(prefix).append('_');
            for (int i = 0; i < 16; i++) {
                sb.append(CROCK[(digest[i] & 0xFF) % 32]);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
