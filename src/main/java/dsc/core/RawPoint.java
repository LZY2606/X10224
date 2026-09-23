package dsc.core;

/**
 * One instrument sample as archived. Original text representations are kept;
 * the derivation layer parses and converts them. Unparseable points are kept
 * with NaN canonical values plus a diagnostic instead of being dropped.
 */
public record RawPoint(int index, String tRaw, String tUnit,
                       String hfRaw, String hfUnit) {
}
