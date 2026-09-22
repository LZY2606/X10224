package dsc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Ids {
    private Ids() {
    }

    public static String hash(String prefix, String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(prefix).append('-');
            for (int i = 0; i < 6; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String eventId(String branch, long seq) {
        return "ev-" + branch.replaceAll("[^a-zA-Z0-9]", "") + "-" + String.format("%04d", seq);
    }
}
