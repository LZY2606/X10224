package dsc.core;

/**
 * Canonical (derivation-layer) point: Kelvin, seconds, endo-up watts and
 * mass-normalized W/g. {@code accepted=false} means the raw point was kept
 * but excluded from integration (e.g. unparseable) — it is still plotted.
 */
public record NormalizedPoint(int index, double tK, double tC, double timeS,
                              double hfW, double hfWg, boolean accepted,
                              String rawT, String rawHf) {
}
