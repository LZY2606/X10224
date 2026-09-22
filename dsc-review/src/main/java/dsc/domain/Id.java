package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stable identity.
 * Source/direct-input objects carry explicit ids (from import manifest).
 * Derived objects carry content-addressed ids: the same inputs+rules always reproduce the same id,
 * so export -> delete -> import preserves identity and provenance.
 */
public final class Id {
    private Id() {}

    public static String of(String kind, String content) {
        return kind + "_" + sha1(content).substring(0, 16);
    }

    public static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
