package dsc.model;

import java.util.concurrent.atomic.AtomicLong;

/** Stable, sortable identifiers: kind-prefix + zero-padded sequence. */
public final class Ids {
    private static final AtomicLong COUNTER = new AtomicLong(System.currentTimeMillis() % 1_000_000);

    private Ids() {}

    public static synchronized String next(String prefix) {
        long v = COUNTER.incrementAndGet();
        return prefix + "-" + String.format("%012d", v);
    }
}
