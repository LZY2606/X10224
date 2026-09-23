package dsc.core;

import java.util.List;

/**
 * Glass-transition step characterization (no enthalpy peak): midpoint, onset
 * and endset temperatures derived from two tangent lines.
 */
public record TgResult(String intervalId, String baselineId,
                      double onsetK, double midK, double endsetK,
                      double deltaCp, List<Diagnostic> diagnostics) {
    public TgResult { diagnostics = List.copyOf(diagnostics); }
}
