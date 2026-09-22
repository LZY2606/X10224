package dsc.state;

import dsc.event.Event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fold of one branch's event chain. Holds only source facts and human
 * decisions; derived numbers live in the recomputation layer.
 */
public class State {

    public String branch = "trunk";
    public String headEventId;
    public long headSeq = -1;

    /** runId -> series source (csv text + parsed summary-free payload). */
    public final Map<String, Map<String, Object>> runs = new LinkedHashMap<>();
    /** massId -> mass source. */
    public final Map<String, Map<String, Object>> masses = new LinkedHashMap<>();
    /** rateId -> declared rate source, keyed by runId then id. */
    public final Map<String, Map<String, Object>> rates = new LinkedHashMap<>();
    /** atmosphereId -> atmosphere source. */
    public final Map<String, Map<String, Object>> atmospheres = new LinkedHashMap<>();
    /** calibrationId -> calibration source. */
    public final Map<String, Map<String, Object>> calibrations = new LinkedHashMap<>();
    /** tangentId -> user selected tangent/fit-range source. */
    public final Map<String, Map<String, Object>> tangents = new LinkedHashMap<>();

    /** windowId -> window payload (semantic stable id). */
    public final Map<String, WindowVersion> windows = new LinkedHashMap<>();
    /** transitionId -> adoption/rejection record. */
    public final Map<String, Decision> decisions = new LinkedHashMap<>();

    public final List<Decision> decisionHistory = new ArrayList<>();

    public final List<Event> events = new ArrayList<>();

    public record WindowVersion(String windowId, String versionId, String segmentId,
                                String tStartRaw, String tEndRaw,
                                Double tStartK, Double tEndK,
                                String createdByEventId, int revision) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("windowId", windowId);
            m.put("versionId", versionId);
            m.put("segmentId", segmentId);
            m.put("tStartRaw", tStartRaw);
            m.put("tEndRaw", tEndRaw);
            m.put("tStartK", tStartK);
            m.put("tEndK", tEndK);
            m.put("createdByEventId", createdByEventId);
            m.put("revision", revision);
            return m;
        }
    }

    public record Decision(String transitionId, String candidateDerivedId,
                           String action, String reasonCode, String note,
                           String eventId, String basedOnCandidateId) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transitionId", transitionId);
            m.put("candidateDerivedId", candidateDerivedId);
            m.put("action", action);
            m.put("reasonCode", reasonCode);
            m.put("note", note);
            m.put("eventId", eventId);
            m.put("basedOnCandidateId", basedOnCandidateId);
            return m;
        }
    }
}
