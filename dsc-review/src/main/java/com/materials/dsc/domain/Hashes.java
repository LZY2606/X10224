package com.materials.dsc.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.TreeMap;

public final class Hashes {
    private Hashes() {
    }

    public static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String shortHash(String s) {
        return sha256(s).substring(0, 12);
    }

    public static String stable(String... parts) {
        TreeMap<String, String> ignored = null;
        return shortHash(String.join("|", parts));
    }
}
