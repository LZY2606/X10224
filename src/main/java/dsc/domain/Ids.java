package dsc.domain;

import java.security.SecureRandom;
import java.time.Instant;

/** Stable, sortable identities: "kind-timestamp-random". Never reused, never recycled. */
public final class Ids {
    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPH = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray();

    private Ids() {}

    public static synchronized String newId(String prefix) {
        long ts = Instant.now().toEpochMilli();
        StringBuilder sb = new StringBuilder(prefix).append('-');
        sb.append(Long.toString(ts, 36)).append('-');
        for (int i = 0; i < 10; i++) sb.append(ALPH[RNG.nextInt(ALPH.length)]);
        return sb.toString();
    }
}
