package dsc.service;

import dsc.domain.BaselineFit;
import dsc.domain.Diagnostic;
import dsc.domain.Interval;
import dsc.domain.Quantity;
import dsc.domain.Segment;
import dsc.domain.TangentSelection;
import dsc.domain.TransitionResult;
import dsc.domain.TransitionType;
import dsc.store.Event;

import java.util.List;
import java.util.Map;

/**
 * Immutable replay result at some event prefix. The UI and export both render
 * from this view, so replaying to an old seq genuinely restores that moment's
 * dependencies and visible conclusions instead of showing fresh numbers.
 */
public record Projection(
        long atSeq,
        Map<String, Segment> segments,
        Map<String, Quantity> masses,
        Map<String, String> massVersionOf,
        Map<String, TangentSelection> tangents,
        Map<String, Map<String, BaselineCandidateView>> baselineCandidates,
        Map<String, Map<String, TransitionResult>> transitionCandidates,
        Map<String, Staleness> staleness,
        Map<String, String> acceptedCandidate,
        List<Event> events,
        List<AnomalyView> anomalies,
        List<String> diagnosticLog
) {
    public record Staleness(boolean stale, String reason, String changedSource) {
        public static Staleness fresh() {
            return new Staleness(false, null, null);
        }
    }

    public record BaselineCandidateView(String id, BaselineFit fit, String ruleVersion,
                                        long eventSeq, String tangentVersion) {
    }

    public record AnomalyView(String code, String severity, String message,
                              String subjectId, long eventSeq, String sourceChainJson) {
    }
}
