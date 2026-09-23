package dsc.service;

import dsc.engine.BaselineEngine;
import dsc.engine.InstrumentParser;
import dsc.engine.TraceNormalizer;
import dsc.engine.TransitionEngine;
import dsc.model.AtmosphereVersion;
import dsc.model.BaselineCandidate;
import dsc.model.BaselineType;
import dsc.model.CalibrationVersion;
import dsc.model.CandidateStatus;
import dsc.model.DecisionState;
import dsc.model.Diagnostic;
import dsc.model.EventKind;
import dsc.model.Interval;
import dsc.model.JournalEvent;
import dsc.model.MassVersion;
import dsc.model.RawSequence;
import dsc.model.RevisionRef;
import dsc.model.ScanRateVersion;
import dsc.model.TangentSelection;
import dsc.model.TraceView;
import dsc.model.TransitionKind;
import dsc.model.TransitionResult;
import dsc.service.payload.Payloads;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure fold of journal events into a {@link WorkspaceState}. No I/O happens
 * here, so time-travel, branches and tests are all just replays of a list.
 */
public final class ReplayService {

    private ReplayService() {}

    public static WorkspaceState replay(List<JournalEvent> events) {
        return replay(events, events.isEmpty() ? 0 : events.get(events.size() - 1).seq(), "main");
    }

    public static WorkspaceState replay(List<JournalEvent> events, long uptoSeq, String branchId) {
        WorkspaceState st = new WorkspaceState();
        st.branchId = branchId;
        List<JournalEvent> seen = new ArrayList<>();
        for (JournalEvent ev : events) {
            if (ev.seq() > uptoSeq) break;
            apply(st, ev);
            seen.add(ev);
            st.headSeq = ev.seq();
        }
        st.events = List.copyOf(seen);
        st.revision = "r" + st.headSeq;
        markStale(st);
        return st;
    }

    private static void apply(WorkspaceState st, JournalEvent ev) {
        switch (ev.kind()) {
            case IMPORT_TRACE -> applyImport(st, ev);
            case EDIT_MASS -> applyMass(st, ev);
            case EDIT_SCAN_RATE -> applyRate(st, ev);
            case EDIT_ATMOSPHERE -> applyAtmosphere(st, ev);
            case EDIT_CALIBRATION -> applyCalibration(st, ev);
            case SELECT_TANGENT -> applyTangent(st, ev);
            case SET_INTERVAL -> applyInterval(st, ev);
            case COMPUTE_BASELINES -> applyCompute(st, ev);
            case ADOPT_BASELINE -> applyAdopt(st, ev);
            case REJECT_BASELINE -> applyReject(st, ev);
            case ADJUST_BASELINE_PARAMS -> applyAdjust(st, ev);
            case CLASSIFY_TRANSITION -> applyClassify(st, ev);
            case CONTINUE_BRANCH, RESOLVE_CONFLICT, RECOMPOTE_FLAG, SYSTEM_NOTE -> { }
        }
    }

    private static void putBaseline(WorkspaceState st, BaselineCandidate c) {
        st.baselineCurrent.put(c.candidateId(), c);
        st.baselineHistory.computeIfAbsent(c.candidateId(), k -> new LinkedHashMap<>()).put(c.version(), c);
    }

    private static void putTransition(WorkspaceState st, TransitionResult t) {
        st.transitionCurrent.put(t.transitionId(), t);
        st.transitionHistory.computeIfAbsent(t.transitionId(), k -> new LinkedHashMap<>()).put(t.version(), t);
    }

    private static TransitionEngineResult produce(WorkspaceState st, String intervalId,
                                                  TangentSelection tangent, long eventSeq) {
        Interval interval = st.intervals.get(intervalId);
        RawSequence raw = st.rawSequences.get(interval.sequenceId());
        CalibrationVersion cal = currentCalibrationFor(st, raw);
        TraceView trace = TraceNormalizer.normalize(raw, cal);
        st.traces.put(interval.sequenceId(), trace);
        BaselineCandidate base = BaselineEngine.build(trace, interval, tangent, 1, eventSeq);
        TransitionKind kind = TransitionKind.UNCLASSIFIED;
        ScanRateVersion rate = st.rateOf(interval.sequenceId());
        MassVersion mass = st.massCurrent.values().stream().findFirst().orElse(null);
        String trId = "tr:" + intervalId + ":" + tangent.tangentId();
        TransitionResult tr = TransitionEngine.integrate(trId, trace, interval, base, rate, mass,
                cal, kind, 1, eventSeq);
        return new TransitionEngineResult(trace, base, tr);
    }

    record TransitionEngineResult(TraceView trace, BaselineCandidate baseline, TransitionResult transition) {}

    static CalibrationVersion currentCalibrationFor(WorkspaceState st, RawSequence raw) {
        return st.calibrationCurrent.values().stream().findFirst()
                .orElse(new CalibrationVersion("cal:default", 1, 0, 0, "factory default", 1));
    }

    // ---------------- event appliers ----------------

    private static void applyImport(WorkspaceState st, JournalEvent ev) {
        Payloads.ImportTrace p = Json.read(ev.payloadJson(), Payloads.ImportTrace.class);
        RawSequence raw = InstrumentParser.parseCsv(p.sourceId(), p.contentCsv());
        st.rawSequences.put(raw.sourceId(), raw);
        CalibrationVersion cal = p.calibrationId() == null ? null
                : st.calibrationCurrent.get(p.calibrationId());
        if (cal == null) cal = currentCalibrationFor(st, raw);
        st.traces.put(raw.sequenceId(), TraceNormalizer.normalize(raw, cal));
    }

    private static void applyMass(WorkspaceState st, JournalEvent ev) {
        Payloads.MassEdit p = Json.read(ev.payloadJson(), Payloads.MassEdit.class);
        st.massRevisions.put(p.massId(), p.revision());
        st.massCurrent.put(p.massId(),
                new MassVersion(p.massId(), p.value(), p.unit(), p.uncertaintyMg(), p.note(), p.revision()));
    }

    private static void applyRate(WorkspaceState st, JournalEvent ev) {
        Payloads.RateEdit p = Json.read(ev.payloadJson(), Payloads.RateEdit.class);
        st.rateRevisions.put(p.rateId(), p.revision());
        st.rateCurrent.put(p.rateId(), new ScanRateVersion(p.rateId(), p.sequenceId(),
                p.value(), p.unit(), p.uncertaintyKPerMin(), p.revision()));
    }

    private static void applyAtmosphere(WorkspaceState st, JournalEvent ev) {
        Payloads.AtmosphereEdit p = Json.read(ev.payloadJson(), Payloads.AtmosphereEdit.class);
        st.atmosphereRevisions.put(p.atmosphereId(), p.revision());
        st.atmosphereCurrent.put(p.atmosphereId(), new AtmosphereVersion(
                p.atmosphereId(), p.sequenceId(), p.gas(), p.flowMlPerMin(), p.revision()));
    }

    private static void applyCalibration(WorkspaceState st, JournalEvent ev) {
        Payloads.CalibrationEdit p = Json.read(ev.payloadJson(), Payloads.CalibrationEdit.class);
        st.calibrationRevisions.put(p.calibrationId(), p.revision());
        st.calibrationCurrent.put(p.calibrationId(), new CalibrationVersion(
                p.calibrationId(), p.slope(), p.offsetC(), p.uncertaintyC(), p.basis(), p.revision()));
        for (Map.Entry<String, RawSequence> e : st.rawSequences.entrySet()) {
            st.traces.put(e.getKey(), TraceNormalizer.normalize(e.getValue(),
                    st.calibrationCurrent.get(p.calibrationId())));
        }
    }

    private static void applyTangent(WorkspaceState st, JournalEvent ev) {
        Payloads.TangentSelect p = Json.read(ev.payloadJson(), Payloads.TangentSelect.class);
        st.tangentRevisions.put(p.tangentId(), p.revision());
        st.tangentCurrent.put(p.tangentId(), new TangentSelection(p.tangentId(), p.sequenceId(),
                p.intervalId(), p.leftStartC(), p.leftEndC(), p.rightStartC(), p.rightEndC(),
                BaselineType.valueOf(p.type()), p.note(), p.revision()));
    }

    private static void applyInterval(WorkspaceState st, JournalEvent ev) {
        Payloads.IntervalSet p = Json.read(ev.payloadJson(), Payloads.IntervalSet.class);
        st.intervals.put(p.intervalId(),
                new Interval(p.intervalId(), p.sequenceId(), p.legId(), p.startC(), p.endC()));
        st.intervalLeg.put(p.intervalId(),
                p.transitionKind() == null ? "UNCLASSIFIED" : p.transitionKind());
    }

    private static void applyCompute(WorkspaceState st, JournalEvent ev) {
        Payloads.ComputeBaselines p = Json.read(ev.payloadJson(), Payloads.ComputeBaselines.class);
        TangentSelection tangent = st.tangentCurrent.get(p.tangentId());
        TransitionEngineResult r = produce(st, p.intervalId(), tangent, ev.seq());
        int bv = st.baselineHistory.getOrDefault(r.baseline.candidateId(), Map.of())
                .keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        int tv = st.transitionHistory.getOrDefault(r.transition.transitionId(), Map.of())
                .keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        BaselineCandidate base = withVersion(r.baseline, bv);
        TransitionResult tr = withVersion(r.transition, tv, base);
        putBaseline(st, base);
        putTransition(st, tr);
        flagOverlaps(st, p.intervalId());
    }

    private static void applyAdopt(WorkspaceState st, JournalEvent ev) {
        Payloads.AdoptBaseline p = Json.read(ev.payloadJson(), Payloads.AdoptBaseline.class);
        boolean anyAdopted = st.baselineCurrent.values().stream()
                .anyMatch(c -> c.intervalId().equals(p.intervalId()) && c.status() == CandidateStatus.ADOPTED);
        for (Map.Entry<String, BaselineCandidate> e : new ArrayList<>(st.baselineCurrent.entrySet())) {
            BaselineCandidate c = e.getValue();
            if (!c.intervalId().equals(p.intervalId())) continue;
            if (c.candidateId().equals(p.candidateId())) {
                e.setValue(c.withStatus(CandidateStatus.ADOPTED));
            } else if (c.status() == CandidateStatus.ADOPTED) {
                e.setValue(c.withStatus(CandidateStatus.ALTERNATIVE));
            }
            st.baselineHistory.get(c.candidateId()).put(c.version(), e.getValue());
        }
        for (Map.Entry<String, TransitionResult> e : new ArrayList<>(st.transitionCurrent.entrySet())) {
            TransitionResult t = e.getValue();
            if (!t.intervalId().equals(p.intervalId())) continue;
            CandidateStatus ns = t.candidateId().equals(p.candidateId())
                    ? CandidateStatus.ADOPTED : CandidateStatus.ALTERNATIVE;
            e.setValue(t.withStatus(ns));
            st.transitionHistory.get(t.transitionId()).put(t.version(), e.getValue());
        }
    }

    private static void applyReject(WorkspaceState st, JournalEvent ev) {
        Payloads.RejectBaseline p = Json.read(ev.payloadJson(), Payloads.RejectBaseline.class);
        BaselineCandidate c = st.baselineCurrent.get(p.candidateId());
        if (c != null) {
            BaselineCandidate rc = c.withStatus(CandidateStatus.REJECTED);
            st.baselineCurrent.put(c.candidateId(), rc);
            st.baselineHistory.get(c.candidateId()).put(c.version(), rc);
        }
        for (Map.Entry<String, TransitionResult> e : new ArrayList<>(st.transitionCurrent.entrySet())) {
            if (e.getValue().candidateId().equals(p.candidateId())) {
                TransitionResult rt = e.getValue().withStatus(CandidateStatus.REJECTED);
                e.setValue(rt);
                st.transitionHistory.get(rt.transitionId()).put(rt.version(), rt);
            }
        }
    }

    private static void applyAdjust(WorkspaceState st, JournalEvent ev) {
        Payloads.AdjustParams p = Json.read(ev.payloadJson(), Payloads.AdjustParams.class);
        TangentSelection old = st.tangentCurrent.get(p.tangentId());
        TangentSelection updated = new TangentSelection(p.tangentId(), old.sequenceId(),
                p.intervalId(), p.leftStartC(), p.leftEndC(), p.rightStartC(), p.rightEndC(),
                BaselineType.valueOf(p.type()), old.note(), p.revision());
        st.tangentRevisions.put(p.tangentId(), p.revision());
        st.tangentCurrent.put(p.tangentId(), updated);
        TransitionEngineResult r = produce(st, p.intervalId(), updated, ev.seq());
        int bv = st.baselineHistory.getOrDefault(r.baseline.candidateId(), Map.of())
                .keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        int tv = st.transitionHistory.getOrDefault(r.transition.transitionId(), Map.of())
                .keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        BaselineCandidate wasAdopted = st.baselineCurrent.values().stream()
                .filter(c -> c.tangentId().equals(p.tangentId()) && c.status() == CandidateStatus.ADOPTED)
                .findFirst().orElse(null);
        BaselineCandidate base = withVersion(r.baseline, bv);
        if (wasAdopted != null) base = base.withStatus(CandidateStatus.ADOPTED);
        TransitionResult tr = withVersion(r.transition, tv, base);
        putBaseline(st, base);
        putTransition(st, tr);
        flagOverlaps(st, p.intervalId());
    }

    private static void applyClassify(WorkspaceState st, JournalEvent ev) {
        Payloads.ClassifyTransition p = Json.read(ev.payloadJson(), Payloads.ClassifyTransition.class);
        TransitionResult t = st.transitionCurrent.get(p.transitionId());
        if (t != null) {
            TransitionResult nt = t.withKind(TransitionKind.valueOf(p.kind()));
            st.transitionCurrent.put(p.transitionId(), nt);
            st.transitionHistory.get(p.transitionId()).put(nt.version(), nt);
        }
    }

    private static BaselineCandidate withVersion(BaselineCandidate c, int v) {
        return new BaselineCandidate(c.candidateId(), c.intervalId(), c.sequenceId(), c.tangentId(),
                c.type(), c.coeffs(), c.fitRmseMW(), c.tgMidC(), c.tgWidthC(), c.status(),
                v, c.basedOnEventSeq(), c.diagnostics());
    }

    private static TransitionResult withVersion(TransitionResult t, int v, BaselineCandidate b) {
        return new TransitionResult(t.transitionId(), b.candidateId(), t.intervalId(), t.sequenceId(),
                t.nature(), t.kind(), t.onsetC(), t.peakC(), t.endsetC(), t.enthalpyMJ(),
                t.enthalpyJPerG(), t.signedAreaMWK(), t.pointCount(), t.massId(), t.rateId(),
                t.calibrationId(), b.status(), v, b.basedOnEventSeq(), t.errorBudget(), t.diagnostics());
    }

    /**
     * Marks overlapping transitions on the same sequence/leg: overlapping
     *转折 are legal but warned about so reviewers know decomposition is non-unique.
     */
    private static void flagOverlaps(WorkspaceState st, String changedIntervalId) {
        List<TransitionResult> all = new ArrayList<>(st.transitionCurrent.values());
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                TransitionResult a = all.get(i), b = all.get(j);
                Interval ia = st.intervals.get(a.intervalId());
                Interval ib = st.intervals.get(b.intervalId());
                if (ia == null || ib == null || !ia.sequenceId().equals(ib.sequenceId())) continue;
                String la = ia.legId() == null ? "" : ia.legId();
                String lb = ib.legId() == null ? "" : ib.legId();
                if (!la.equals(lb)) continue;
                boolean overlap = Math.min(ia.endC(), ib.endC()) > Math.max(ia.startC(), ib.startC());
                if (overlap) {
                    String msg = "interval overlaps " + b.intervalId()
                            + "; overlapping transitions have non-unique baseline decomposition";
                    attachWarning(st, a, "OVERLAPPING_TRANSITIONS", msg);
                    attachWarning(st, b, "OVERLAPPING_TRANSITIONS",
                            "interval overlaps " + a.intervalId()
                                    + "; overlapping transitions have non-unique baseline decomposition");
                }
            }
        }
    }

    private static void attachWarning(WorkspaceState st, TransitionResult t, String code, String msg) {
        boolean exists = t.diagnostics().stream().anyMatch(d -> d.code().equals(code) && d.message().equals(msg));
        if (exists) return;
        List<Diagnostic> diags = new ArrayList<>(t.diagnostics());
        diags.add(Diagnostic.warn(code, msg));
        TransitionResult nt = new TransitionResult(t.transitionId(), t.candidateId(), t.intervalId(),
                t.sequenceId(), t.nature(), t.kind(), t.onsetC(), t.peakC(), t.endsetC(),
                t.enthalpyMJ(), t.enthalpyJPerG(), t.signedAreaMWK(), t.pointCount(), t.massId(),
                t.rateId(), t.calibrationId(), t.status(), t.version(), t.basedOnEventSeq(),
                t.errorBudget(), diags);
        st.transitionCurrent.put(t.transitionId(), nt);
        st.transitionHistory.get(t.transitionId()).put(nt.version(), nt);
    }

    // ---------------- dependency graph / staleness ----------------

    /**
     * Dependency-graph propagation pass. A derivation is STALE iff any input
     * revision it was based on differs from the current revision of that
     * input. Mass revisions only invalidate the normalized (J/g) half of a
     * transition; the raw mJ area remains VALID with an explanation.
     */
    static void markStale(WorkspaceState st) {
        for (TransitionResult t : new ArrayList<>(st.transitionCurrent.values())) {
            if (t.status() == CandidateStatus.REJECTED) continue;
            boolean traceStale = false;
            boolean massStale = false;
            boolean rateStale = false;
            List<String> reasons = new ArrayList<>();

            CalibrationVersion cal = st.calibrationCurrent.get(t.calibrationId());
            int calRev = cal == null ? 1 : cal.revision();
            Interval interval = st.intervals.get(t.intervalId());
            String seqId = interval == null ? t.sequenceId() : interval.sequenceId();
            RawSequence raw = st.rawSequences.get(seqId);
            if (raw != null) {
                TraceView fresh = TraceNormalizer.normalize(raw,
                        cal == null ? currentCalibrationFor(st, raw) : cal);
                TraceView had = st.traces.get(seqId);
                if (had == null) st.traces.put(seqId, fresh);
            }
            if (t.basedOnEventSeq() < st.headSeq) {
                if (changedAfter(st, t, EventKind.EDIT_CALIBRATION, t.calibrationId())) {
                    traceStale = true;
                    reasons.add("temperature calibration revised after this result was computed");
                }
                if (changedAfter(st, t, EventKind.IMPORT_TRACE, null)) {
                    traceStale = true;
                    reasons.add("instrument output re-imported after this result was computed");
                }
                if (changedAfter(st, t, EventKind.EDIT_SCAN_RATE, null)) {
                    rateStale = true;
                    reasons.add("scan rate revised; time-normalized enthalpy needs recomputation");
                }
                if (changedAfter(st, t, EventKind.SET_INTERVAL, t.intervalId())
                        || tangentChangedAfter(st, t)) {
                    traceStale = true;
                    reasons.add("interval or tangent changed after this result was computed");
                }
            }
            MassVersion mass = t.massId() == null ? null : st.massCurrent.get(t.massId());
            if (mass != null && changedAfter(st, t, EventKind.EDIT_MASS, t.massId())) {
                massStale = true;
                reasons.add("sample mass revised; all normalized derivations (J/g) are expired");
            }
            if (!traceStale && !massStale && !rateStale) continue;

            List<Diagnostic> diags = new ArrayList<>(t.diagnostics());
            diags.removeIf(d -> d.code().startsWith("STALE_"));
            if (traceStale) diags.add(Diagnostic.warn("STALE_TRACE_DEPENDENCY",
                    "result is stale: " + String.join("; ", reasons)));
            else if (rateStale) diags.add(Diagnostic.warn("STALE_RATE_DEPENDENCY",
                    "raw area is still valid, but enthalpy (mJ) is stale: " + String.join("; ", reasons)));
            else diags.add(Diagnostic.warn("STALE_MASS_DEPENDENCY",
                    "raw enthalpy (mJ) is still valid, but normalized enthalpy (J/g) is stale: "
                            + String.join("; ", reasons)));
            TransitionResult stale = new TransitionResult(t.transitionId(), t.candidateId(),
                    t.intervalId(), t.sequenceId(), t.nature(), t.kind(), t.onsetC(), t.peakC(),
                    t.endsetC(), t.enthalpyMJ(),
                    massStale ? null : t.enthalpyJPerG(),
                    t.signedAreaMWK(), t.pointCount(), t.massId(), t.rateId(), t.calibrationId(),
                    CandidateStatus.STALE, t.version(), t.basedOnEventSeq(), t.errorBudget(), diags);
            st.transitionCurrent.put(t.transitionId(), stale);
            st.transitionHistory.get(t.transitionId()).put(t.version(), stale);
        }
        for (BaselineCandidate c : new ArrayList<>(st.baselineCurrent.values())) {
            if (c.status() == CandidateStatus.REJECTED) continue;
            boolean stale = calibrationChangedAfter(st, c)
                    || changedAfter(st, c, EventKind.IMPORT_TRACE, null)
                    || changedAfter(st, c, EventKind.SET_INTERVAL, c.intervalId())
                    || tangentChangedAfter(st, c);
            if (stale && c.status() != CandidateStatus.STALE) {
                BaselineCandidate sc = c.withStatus(CandidateStatus.STALE);
                st.baselineCurrent.put(c.candidateId(), sc);
                st.baselineHistory.get(c.candidateId()).put(c.version(), sc);
            }
        }
    }

    private static boolean changedAfter(WorkspaceState st, TransitionResult t, EventKind kind, String id) {
        for (JournalEvent ev : st.events) {
            if (ev.seq() <= t.basedOnEventSeq()) continue;
            if (ev.kind() != kind) continue;
            if (id == null) return true;
            if (payloadMentions(ev, id)) return true;
        }
        return false;
    }

    private static boolean changedAfter(WorkspaceState st, BaselineCandidate c, EventKind kind, String id) {
        for (JournalEvent ev : st.events) {
            if (ev.seq() <= c.basedOnEventSeq()) continue;
            if (ev.kind() != kind) continue;
            if (id == null || payloadMentions(ev, id) || payloadMentions(ev, c.intervalId())) return true;
        }
        return false;
    }

    private static boolean payloadMentions(JournalEvent ev, String token) {
        return ev.payloadJson() != null && ev.payloadJson().contains(token);
    }

    private static boolean calibrationChangedAfter(WorkspaceState st, BaselineCandidate c) {
        for (JournalEvent ev : st.events) {
            if (ev.seq() > c.basedOnEventSeq() && ev.kind() == EventKind.EDIT_CALIBRATION) return true;
        }
        return false;
    }

    private static boolean tangentChangedAfter(WorkspaceState st, BaselineCandidate c) {
        for (JournalEvent ev : st.events) {
            if (ev.seq() > c.basedOnEventSeq() && ev.kind() == EventKind.SELECT_TANGENT
                    && payloadMentions(ev, c.tangentId())) return true;
        }
        return false;
    }

    private static boolean tangentChangedAfter(WorkspaceState st, TransitionResult t) {
        for (JournalEvent ev : st.events) {
            if (ev.seq() > t.basedOnEventSeq() && ev.kind() == EventKind.SELECT_TANGENT
                    && payloadMentions(ev, t.intervalId())) return true;
        }
        return false;
    }
}
