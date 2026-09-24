package dsc.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable identity helpers. Identities are content-scoped so re-import keeps them. */
public final class Ids {
    private Ids() {}

    /** Deterministic UUIDv5-style id from a logical key; stable across export/import/recompute. */
    public static UUID stable(String key) {
        return UUID.nameUUIDFromBytes(("dsc:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static String sid(String key) {
        return stable(key).toString();
    }

    public static String random() {
        return UUID.randomUUID().toString();
    }
}
