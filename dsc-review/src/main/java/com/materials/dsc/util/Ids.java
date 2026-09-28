package com.materials.dsc.util;

import java.security.SecureRandom;

/**
 * Stable, prefixed identifiers.  The prefix records the entity kind so an id
 * found inside an export is self-describing; the random suffix keeps history
 * merges collision-free.
 */
public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray();

    private Ids() {
    }

    public static String make(String prefix) {
        long ts = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder(prefix).append('_');
        for (int i = 4; i >= 0; i--) {
            sb.append(ALPHABET[(int) ((ts >>> (i * 5)) & 31)]);
        }
        byte[] noise = new byte[8];
        RANDOM.nextBytes(noise);
        for (byte b : noise) {
            sb.append(ALPHABET[(b & 31)]);
        }
        return sb.toString();
    }
}
