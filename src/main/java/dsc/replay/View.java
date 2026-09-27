package dsc.replay;

import dsc.model.Branch;
import dsc.model.Derivation;
import dsc.model.Event;
import dsc.model.Interval;
import dsc.model.Source;
import dsc.model.SourceKind;
import dsc.rules.SourceSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable materialization of one event-tip path: what the reviewer saw at that moment. */
public record View(
        String projectId,
        Branch pointer,
        List<Event> chain,
        Map<SourceKind, List<Source>> sourceHistory,
        Map<SourceKind, Source> currentSources,
        List<Interval> intervals,
        Map<String, String> acceptedBaseline,
        Map<String, String> rejectedBaseline,
        List<Derivation> derivations,
        List<String> diagnostics
) {
    public SourceSnapshot snapshot() {
        List<String> diags = new ArrayList<>(diagnostics);
        return new SourceSnapshot(
                currentSources.get(SourceKind.INSTRUMENT_OUTPUT),
                currentSources.get(SourceKind.SAMPLE_MASS),
                currentSources.get(SourceKind.SCAN_RATE),
                currentSources.get(SourceKind.ATMOSPHERE),
                currentSources.get(SourceKind.TEMPERATURE_CALIBRATION),
                currentSources.get(SourceKind.USER_TANGENT),
                diags);
    }

    public Event tip() {
        return chain.isEmpty() ? null : chain.get(chain.size() - 1);
    }

    public long tipSeq() {
        Event t = tip();
        return t == null ? 0 : t.seq();
    }
}
