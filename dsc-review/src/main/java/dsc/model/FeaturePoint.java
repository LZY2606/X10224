package dsc.model;

/** onset / peak / endset 特征点，值带单位（°C）与稳定显示身份。 */
public final class FeaturePoint {
    public String pointId;
    public String role;                 // onset | peak | endset | tg_mid
    public double tempC;
    public Double hfMw;
    public String method;

    public FeaturePoint() {}

    public FeaturePoint(String pointId, String role, double tempC, Double hfMw, String method) {
        this.pointId = pointId;
        this.role = role;
        this.tempC = tempC;
        this.hfMw = hfMw;
        this.method = method;
    }
}
