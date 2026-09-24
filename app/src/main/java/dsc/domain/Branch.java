package dsc.domain;

/** A review branch. Multiple legal answers coexist; choosing one never deletes the others. */
public class Branch {
    public String id;
    public String sampleId;
    public String name;
    public String parentBranchId;
    public String forkFromEventId;
    public long createdAt;
    public boolean selected;
    public String reason;
}
