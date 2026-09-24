package io.dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Canonical SHA-256 hashing for the append-only event chain. */
public final class Hashes {
    private Hashes() {}

    public static final ObjectMapper CANONICAL = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String eventHash(String branchId, long seq, String type, long occurredAt,
                                   String actor, String payloadJson, String prevHash) {
        String canonical = String.join("|",
                branchId, Long.toString(seq), type, Long.toString(occurredAt),
                actor == null ? "" : actor, payloadJson, prevHash == null ? "" : prevHash);
        return sha256Hex(canonical);
    }
}
