package dsc.domain;

/** A value/interpretation diagnostic. Unconvertible values stay in place and get one of these. */
public class Diagnostic {
    public String id;
    public String derivationId;
    public int derivationVersion;
    public String segmentId;
    public String intervalId;
    public Enums.PolicyRule rule;
    public Enums.Severity severity;
    public Enums.Verdict verdict;
    public String message;
    public String evidenceJson;
    public long createdAt;
}
