package dsc.model;

import com.google.gson.JsonElement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

/** Stable identity + content version hashing and deterministic time. */
public final class Ids {

    private Ids() {}

    public static String hash12(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < parts.length; i++) {
                md.update(parts[i] == null ? "" : parts[i].getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1f);
            }
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Deterministic id for objects recreated by case-pack replay. */
    public static String deterministic(String tag, String discriminator) {
        return tag + "_" + hash12(tag, discriminator);
    }

    public static String random(String tag) {
        return tag + "_" + UUID.randomUUID().toString().substring(0, 12);
    }

    public static String docVersion(String kind, String lineage, String baseVersion, JsonElement data) {
        return hash12("doc", kind, lineage, baseVersion == null ? "" : baseVersion, J.canonical(data));
    }

    /** Replay clock: monotonic deterministic timestamps, overridable for live edits. */
    public static String replayTime(long seq) {
        return String.format("2026-05-04T09:%02d:%02dZ", 30 + (int) (seq / 60), seq % 60);
    }

    public static String now() {
        return Instant.now().toString();
    }
}
