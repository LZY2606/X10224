package dsc.domain;

/**
 * 用户选定的切线：前切线窗口与后切线窗口（温度区间，开尔文）。
 * 与仪器输出、质量、速率、气氛、标定一样独立存档（sourceId 指向切线来源文档）。
 */
public final class TangentChoice {
    public final String tangentId;
    public final String segmentId;
    public final double preStartK;
    public final double preEndK;
    public final double postStartK;
    public final double postEndK;
    public final String basis; // 选择依据（人工说明）
    public final String decidedBy;

    public TangentChoice(String tangentId, String segmentId, double preStartK, double preEndK,
                         double postStartK, double postEndK, String basis, String decidedBy) {
        this.tangentId = tangentId;
        this.segmentId = segmentId;
        this.preStartK = preStartK;
        this.preEndK = preEndK;
        this.postStartK = postStartK;
        this.postEndK = postEndK;
        this.basis = basis;
        this.decidedBy = decidedBy;
    }
}
