package dsc.model;

/** 用户选定的切线分别存档：以左右锚点区间表示，独立于仪器输出。 */
public final class TangentSelection {
    public String versionId;
    public String createdEvent;
    public String segmentId;
    public String windowId;
    public Quantity leftFromC;
    public Quantity leftToC;
    public Quantity rightFromC;
    public Quantity rightToC;
    public String note;

    public TangentSelection() {}

    public boolean appliesTo(String seg, String win) {
        return segmentId.equals(seg) && windowId.equals(win);
    }
}
