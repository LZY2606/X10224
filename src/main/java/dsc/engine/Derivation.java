package dsc.engine;

import dsc.model.BaselineCandidate;
import dsc.model.Diagnostic;
import dsc.model.Integration;
import dsc.model.Judgment;
import dsc.model.ThermalEvent;

import java.util.List;

public record Derivation(
        List<BaselineCandidate> baselines,
        List<ThermalEvent> thermalEvents,
        List<Integration> integrations,
        List<Diagnostic> diagnostics,
        List<Judgment> judgments
) {
}
