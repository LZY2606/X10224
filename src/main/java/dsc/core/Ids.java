package dsc.core;

/**
 * Stable identity helpers.
 *
 * Identities are never derived from a row number, an array index or a file
 * name: raw, imported objects carry the identifiers declared by the case
 * package, so export / delete / import preserves identity, order and
 * provenance relations.
 */
public final class Ids {
    private Ids() {}

    public static String stable(String... parts) {
        return String.join("/", parts);
    }

    public static String deriv(String kind, String target, String ruleVersion) {
        return kind + ":" + target + "@" + ruleVersion;
    }
}
