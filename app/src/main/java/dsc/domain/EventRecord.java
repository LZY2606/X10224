package dsc.domain;

/**
 * Append-only event. The event log is the replayable narrative; nothing mutates past events.
 * {@code branchId}/{@code parentEventId} allow forks so an old event can be revisited without
 * overwriting the latest conclusions.
 */
public class EventRecord {
    public long seq;
    public String eventId;
    public String branchId;
    public String parentEventId; // previous event on this branch (fork point for new branches)
    public Enums.EventKind kind;
    public String subjectType;   // SOURCE / DERIVATION / SAMPLE / RULESET
    public String subjectId;
    public int subjectVersion;
    public String actor;
    public String detailJson;    // machine-readable payload incl. affected objects & verdicts
    public long createdAt;
    public String note;
}
