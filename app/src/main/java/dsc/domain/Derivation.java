package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * An independently versioned derived result. A new row is written on every recompute; the current
 * pointer switches only after the whole write succeeds (atomic commit). Stable id is scoped by its
 * logical key, and every version keeps the exact source/rule versions it was computed from.
 */
public class Derivation {
    public String id;           // stable logical identity (same across versions)
    public int version;
    public String sampleId;
    public String branchId;
    public Enums.DerivKind kind;
    public String segmentId;
    public String intervalId;   // NORMALIZED_CURVE -> null
    public boolean current;
    public String status;       // VALID, STALE, ERROR, INDETERMINATE
    public String basisJson;    // full numeric result (baseline coefs, onset/peak/endset, dH...)
    public List<DepEdge> deps = new ArrayList<>();
    public String message;
    public long createdAt;
    public String createdBy;
    public Enums.CandidateStatus candidateStatus = Enums.CandidateStatus.CANDIDATE;
    public String method;       // e.g. linear / tangent-step / glass-step

    public static class DepEdge {
        public String refType;  // SOURCE / DERIVATION / SEGMENT / RULESET
        public String refId;
        public int refVersion;
        public DepEdge() {}
        public DepEdge(String refType, String refId, int refVersion) {
            this.refType = refType; this.refId = refId; this.refVersion = refVersion;
        }
    }
}
