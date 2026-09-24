package dsc.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicLong;

/** Stable identity: content-derived ids survive export/delete/import round-trips. */
public final class Ids {
    private static final AtomicLong SEQ = new AtomicLong(1);

    private Ids() {}

    public static String stable(String kind, String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", dig[i]));
            return kind + "-" + sb;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Deterministic sequence id, namespaced per JVM run; used with content salt for stability. */
    public static String seq(String kind, String salt) {
        return stable(kind, salt + "#" + SEQ.getAndIncrement());
    }
}
