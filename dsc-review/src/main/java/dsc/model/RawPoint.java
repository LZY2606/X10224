package dsc.model;

/** 仪器输出中的一个原始采样点。温度/热流均保留原始 token 与解析诊断。 */
public final class RawPoint {
    public int index;
    public String tempRaw;
    public String hfRaw;
    public Double tempC;
    public Double hfMw;
    public String diagnostic;

    public RawPoint() {}

    public RawPoint(int index, String tempRaw, String hfRaw) {
        this.index = index;
        this.tempRaw = tempRaw;
        this.hfRaw = hfRaw;
    }

    public boolean usable() {
        return tempC != null && hfMw != null && diagnostic == null;
    }
}
