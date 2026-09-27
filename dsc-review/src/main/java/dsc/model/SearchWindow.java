package dsc.model;

/** 审阅者在曲线页上拖动得到的搜索区间（可反复调整，每次产生事件）。 */
public final class SearchWindow {
    public String windowId;
    public String segmentId;
    public double fromC;
    public double toC;
    public int fromEventSeq;

    public SearchWindow() {}

    public SearchWindow(String windowId, String segmentId, double fromC, double toC, int fromEventSeq) {
        this.windowId = windowId;
        this.segmentId = segmentId;
        this.fromC = fromC;
        this.toC = toC;
        this.fromEventSeq = fromEventSeq;
    }

    public double lo() { return Math.min(fromC, toC); }
    public double hi() { return Math.max(fromC, toC); }

    public boolean overlaps(SearchWindow o) {
        if (!segmentId.equals(o.segmentId)) return false;
        return lo() < o.hi() && o.lo() < hi();
    }
}
