package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only fact of the sample history. Everything reviewable is an event;
 * projections are rebuilt from this log, so rewinding to an old event restores
 * exactly the dependency versions and visible conclusions of that moment.
 */
public final class AuditEvent {
    public String id;
    public int seq;
    public String caseId;
    public String type;                 // see EventTypes
    public long timestamp;
    public String actor;
    public String baseEtag;             // optimistic-concurrency token held by the editor
    public EventStatus status = EventStatus.APPLIED;
    public String payloadJson;
    public List<Judgment> judgments = new ArrayList<>();
    public List<String> affectedDerivedIds = new ArrayList<>();
    public List<String> replayChecks = new ArrayList<>(); // boundary scenarios actually exercised
    public String supersededByEventId;

    public AuditEvent() {}
}
