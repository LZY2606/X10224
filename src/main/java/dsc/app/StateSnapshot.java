package dsc.app;

import dsc.domain.Atmosphere;
import dsc.domain.BaselineCandidate;
import dsc.domain.Event;
import dsc.domain.Interval;
import dsc.domain.Qty;
import dsc.domain.Sample;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.Versioned;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable view of the whole sample history at one point in the event log. */
public record StateSnapshot(
        Sample sample,
        Map<String, Segment> segments,
        Map<String, String> segmentExoConvention,
        Map<String, String> segmentMeasuredDirection,
        List<Versioned<Qty>> massVersions,
        List<Versioned<Qty>> rateVersions,
        List<Versioned<Atmosphere>> atmosphereVersions,
        List<Versioned<Qty>> calibrationVersions,
        Map<String, List<Versioned<TangentSelection>>> tangentVersions,
        List<Interval> intervals,
        Map<String, List<BaselineCandidate>> candidatesByInterval,
        Map<String, List<String>> adoptedCandidates,
        Map<String, List<String>> rejectedCandidates,
        Map<String, TransitionResult> transitions,
        List<Event> events,
        List<String> diagnostics) {

    public static StateSnapshot empty() {
        return new StateSnapshot(null, new LinkedHashMap<>(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), List.of(), List.of(), List.of(), List.of(),
                new LinkedHashMap<>(), List.of(), new LinkedHashMap<>(),
                new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                List.of(), List.of());
    }

    public Versioned<Qty> currentMass() {
        return massVersions.isEmpty() ? null : massVersions.get(massVersions.size() - 1);
    }

    public Versioned<Qty> currentRate() {
        return rateVersions.isEmpty() ? null : rateVersions.get(rateVersions.size() - 1);
    }

    public Versioned<Qty> currentCalibration() {
        return calibrationVersions.isEmpty() ? null : calibrationVersions.get(calibrationVersions.size() - 1);
    }

    public Versioned<Atmosphere> currentAtmosphere() {
        return atmosphereVersions.isEmpty() ? null : atmosphereVersions.get(atmosphereVersions.size() - 1);
    }
}
