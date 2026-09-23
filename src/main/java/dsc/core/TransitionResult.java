package dsc.core;

import java.util.List;

/**
 * Derived phase-transition characterization for one (interval, baseline) pair.
 *
 * Sign is reported in the canonical endo-up frame together with the declared
 * scan direction: the same physical event is endothermic on heating and
 * exothermic on cooling (crystallization) etc.
 */
public record TransitionResult(
        String intervalId,
        String baselineId,
        String eventKind,
        double onsetK,
        double peakK,
        double endsetK,
        double deltaHJg,
        String directionOnSegment,
        EnthalpyErrorSources errors,
        List<Diagnostic> diagnostics) {

    public TransitionResult { diagnostics = List.copyOf(diagnostics); }
}
