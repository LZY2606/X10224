package dsc.domain;

/**
 * 气氛来源文档（独立存档）。扫描段引用其 id；气氛修订只影响引用它的段派生。
 */
public final class AtmosphereRecord {
    public final String atmosphereId;
    public final String gas;            // N2 | Ar | air ...
    public final double flowMlPerMin;
    public final String rawFlowText;
    public final String recordedAt;

    public AtmosphereRecord(String atmosphereId, String gas, double flowMlPerMin,
                            String rawFlowText, String recordedAt) {
        this.atmosphereId = atmosphereId;
        this.gas = gas;
        this.flowMlPerMin = flowMlPerMin;
        this.rawFlowText = rawFlowText;
        this.recordedAt = recordedAt;
    }
}
