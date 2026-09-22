package dsc.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public final class Hashing {

    private Hashing() {
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String eventId(Map<String, Object> bodyForHash) {
        return "E-" + sha256Hex(Canonical.json(bodyForHash)).substring(0, 24);
    }

    public static String derivedId(String nodeId, String inputsHash) {
        return "D-" + sha256Hex(nodeId + "|" + inputsHash).substring(0, 24);
    }
}
