package dsc.domain;

import java.util.UUID;

/** Stable identity generator. Ids are assigned once at creation and survive export/import. */
public final class Ids {
    private Ids() {}

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 12);
    }
}
