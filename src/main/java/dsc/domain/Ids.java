package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable content-addressed identities. The same logical fact (same inputs,
 * same rule version, same parameters) produces the same id in every database,
 * so an export/delete/import round-trip preserves identities and provenance.
 */
public final class Ids {

    private Ids() {}

    public static String sha(String prefix, String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(prefix.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            for (String p : parts) {
                md.update((byte) 1);
                md.update(p == null ? new byte[0] : p.getBytes(StandardCharsets.UTF_8));
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder(prefix).append('-');
            for (int i = 0; i < 10; i++) {
                sb.append(String.format("%02x", d[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
