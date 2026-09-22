package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A human adjudication. Accepting one candidate never deletes the alternatives, and a
 * later conflicting decision is kept (resolved by explicit supersede) rather than silently
 * overwriting the previous one.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Decision {
    public String id;
    public String action;
    public String intervalId;
    public String candidateId;
    public String rejectedCandidateId;
    public String comment;
    public String actor;
    public long eventSeq;
    public String status;
    public String supersededBy;

    public Decision() {
    }
}
