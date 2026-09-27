package dsc.model;

/** 气氛记录（气体、流量），与仪器输出分别存档。 */
public final class AtmosphereRecord {
    public String versionId;
    public String gas;
    public Quantity flow;               // mL/min
    public String note;

    public AtmosphereRecord() {}

    public AtmosphereRecord(String versionId, String gas, Quantity flow, String note) {
        this.versionId = versionId;
        this.gas = gas;
        this.flow = flow;
        this.note = note;
    }
}
