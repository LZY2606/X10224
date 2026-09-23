package dsc.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Content fingerprints used by the dependency graph for change detection. */
public final class Hashes {

    public static final ObjectMapper MAPPER = new ObjectMapper();

    private Hashes() {}

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String shortHash(Object o) {
        try {
            String json = MAPPER.writer().writeValueAsString(o);
            return sha256(json).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
