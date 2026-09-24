package dsc.store;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

public final class Ids {
    private Ids() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    public static String now() {
        return Instant.now().toString();
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
