package dsc.model;

/** 温度标定（对所有段统一施加的可溯源平移，单位与基准显式携带）。 */
public final class Calibration {
    public String versionId;
    public String createdEvent;
    public Quantity offsetC;            // T_calibrated = T_ind + offset
    public String standard;
    public String note;

    public Calibration() {}

    public Calibration(String versionId, String createdEvent, Quantity offsetC, String standard, String note) {
        this.versionId = versionId;
        this.createdEvent = createdEvent;
        this.offsetC = offsetC;
        this.standard = standard;
        this.note = note;
    }
}
