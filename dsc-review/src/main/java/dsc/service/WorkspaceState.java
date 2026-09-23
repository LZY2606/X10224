package dsc.service;

import dsc.model.AtmosphereVersion;
import dsc.model.BaselineCandidate;
import dsc.model.CalibrationVersion;
import dsc.model.Interval;
import dsc.model.JournalEvent;
import dsc.model.MassVersion;
import dsc.model.RawSequence;
import dsc.model.ScanRateVersion;
import dsc.model.TangentSelection;
import dsc.model.TraceView;
import dsc.model.TransitionResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable-ish snapshot of one branch replayed to a given journal sequence.
 * All derivations keep their full version history; {@code current*} maps hold
 * the winning version per stable id.
 */
public final class WorkspaceState {
    public long headSeq;
    public String branchId = "main";
    public String revision = "r0";
    public final Map<String, RawSequence> rawSequences = new LinkedHashMap<>();
    public final Map<String, TraceView> traces = new LinkedHashMap<>();
    public final Map<String, MassVersion> massCurrent = new LinkedHashMap<>();
    public final Map<String, ScanRateVersion> rateCurrent = new LinkedHashMap<>();
    public final Map<String, AtmosphereVersion> atmosphereCurrent = new LinkedHashMap<>();
    public final Map<String, CalibrationVersion> calibrationCurrent = new LinkedHashMap<>();
    public final Map<String, TangentSelection> tangentCurrent = new LinkedHashMap<>();
    public final Map<String, Interval> intervals = new LinkedHashMap<>();
    public final Map<String, BaselineCandidate> baselineCurrent = new LinkedHashMap<>();
    public final Map<String, TransitionResult> transitionCurrent = new LinkedHashMap<>();
    public final Map<String, Map<Integer, BaselineCandidate>> baselineHistory = new LinkedHashMap<>();
    public final Map<String, Map<Integer, TransitionResult>> transitionHistory = new LinkedHashMap<>();
    public final Map<String, Integer> massRevisions = new LinkedHashMap<>();
    public final Map<String, Integer> rateRevisions = new LinkedHashMap<>();
    public final Map<String, Integer> atmosphereRevisions = new LinkedHashMap<>();
    public final Map<String, Integer> calibrationRevisions = new LinkedHashMap<>();
    public final Map<String, Integer> tangentRevisions = new LinkedHashMap<>();
    public final Map<String, String> intervalLeg = new LinkedHashMap<>();
    public List<JournalEvent> events = List.of();

    public List<String> sequenceIds() { return rawSequences.keySet().stream().toList(); }

    public MassVersion massOf(String id) { return massCurrent.get(id); }
    public ScanRateVersion rateOf(String sequenceId) {
        return rateCurrent.values().stream().filter(r -> r.sequenceId().equals(sequenceId))
                .findFirst().orElse(null);
    }
    public CalibrationVersion calibrationOf(String id) { return calibrationCurrent.get(id); }
}
