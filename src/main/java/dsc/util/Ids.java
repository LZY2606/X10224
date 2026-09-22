package dsc.util;

import java.util.UUID;

public final class Ids {
    public static String newId() { return UUID.randomUUID().toString(); }
    private Ids() {}
}
