package dsc.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Content-hash helper shared by result ids and baseline candidate ids. */
public final class TransitionIdsHash {

    private TransitionIdsHash() {
    }

    public static String hash(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p0 : parts) {
                md.update((p0 == null ? "\u0000" : p0).getBytes(StandardCharsets.UTF_8));
                md.update((byte) 0x1f);
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
