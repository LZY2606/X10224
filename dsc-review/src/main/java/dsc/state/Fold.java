package dsc.state;

import dsc.event.Canonical;
import dsc.event.Event;
import dsc.event.Hashing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reduces an event chain to {@link State}. Pure: replay to any event restores
 * the facts as they were then, without touching derived results.
 */
public final class Fold {

    private Fold() {
    }

    public static State fold(List<Event> events, String branch) {
        State s = new State();
        s.branch = branch;
        for (Event e : events) {
            apply(s, e);
        }
        return s;
    }

    private static void apply(State s, Event e) {
        s.events.add(e);
        s.headEventId = e.id();
        s.headSeq = e.seq();
        Map<String, Object> p = e.payload();
        switch (e.type()) {
            case "RUN_IMPORTED", "RUN_FRAGMENT_PATCHED" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> run = new LinkedHashMap<>(
                        (Map<String, Object>) p.getOrDefault("run", p));
                run.put("importedByEventId", e.id());
                run.put("importedAtMillis", e.createdAtMillis());
                s.runs.put(str(run.get("runId")), run);
            }
            case "MASS_RECORDED" -> s.masses.put(str(p.get("massId")), versioned(p, e));
            case "RATE_RECORDED" -> s.rates.put(str(p.get("rateId")), versioned(p, e));
            case "ATMOSPHERE_RECORDED" ->
                    s.atmospheres.put(str(p.get("atmosphereId")), versioned(p, e));
            case "CALIBRATION_RECORDED" ->
                    s.calibrations.put(str(p.get("calibrationId")), versioned(p, e));
            case "TANGENT_RECORDED" ->
                    s.tangents.put(str(p.get("tangentId")), versioned(p, e));
            case "WINDOW_SET" -> applyWindow(s, e);
            case "BASELINE_ADOPTED", "BASELINE_REJECTED" -> applyDecision(s, e);
            default -> {
                // marker/audit events need no folded state
            }
        }
    }

    private static Map<String, Object> versioned(Map<String, Object> p, Event e) {
        Map<String, Object> m = new LinkedHashMap<>(p);
        m.put("recordedByEventId", e.id());
        return m;
    }

    private static void applyWindow(State s, Event e) {
        Map<String, Object> p = e.payload();
        String windowId = str(p.get("windowId"));
        int revision = (s.windows.containsKey(windowId)
                ? s.windows.get(windowId).revision() : 0) + 1;
        Map<String, Object> hashBody = new LinkedHashMap<>();
        hashBody.put("windowId", windowId);
        hashBody.put("segmentId", p.get("segmentId"));
        hashBody.put("tStartRaw", p.get("tStartRaw"));
        hashBody.put("tEndRaw", p.get("tEndRaw"));
        hashBody.put("revision", revision);
        String versionId = "WV-" + Hashing.sha256Hex(Canonical.json(hashBody)).substring(0, 16);
        s.windows.put(windowId, new State.WindowVersion(
                windowId, versionId, str(p.get("segmentId")),
                str(p.get("tStartRaw")), str(p.get("tEndRaw")),
                dbl(p.get("tStartK")), dbl(p.get("tEndK")),
                e.id(), revision));
    }

    private static void applyDecision(State s, Event e) {
        Map<String, Object> p = e.payload();
        State.Decision d = new State.Decision(
                str(p.get("transitionId")),
                str(p.get("candidateDerivedId")),
                e.type().equals("BASELINE_ADOPTED") ? "adopt" : "reject",
                str(p.get("reasonCode")),
                str(p.get("note")),
                e.id(),
                str(p.get("basedOnCandidateId")));
        s.decisions.put(d.transitionId(), d);
        s.decisionHistory.add(d);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static Double dbl(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    public static List<State.Decision> decisionHistory(State s) {
        return s.decisionHistory;
    }

    public static void ensureHistoryField(State s) {
        // no-op kept for clarity; field initialized in State
    }

    public static Map<String, Object> latestByRevision(Map<String, Map<String, Object>> src) {
        Map<String, Object> best = null;
        int bestRev = -1;
        for (Map<String, Object> m : src.values()) {
            Object r = m.get("revision");
            int rev = r instanceof Number n ? n.intValue() : 1;
            if (rev > bestRev) {
                bestRev = rev;
                best = m;
            }
        }
        if (best == null) {
            return null;
        }
        return best;
    }
}
