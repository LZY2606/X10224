package dsc.model;

/** 规范化后的点：温度已标定（°C, ITS-90 近似基准），热流统一到 exo-up 的 mW 与 J/(g·K)。 */
public final class NormalizedPoint {
    public int index;
    public double tempC;
    public double hfMw;
    public double hfJgK;

    public NormalizedPoint() {}

    public NormalizedPoint(int index, double tempC, double hfMw, double hfJgK) {
        this.index = index;
        this.tempC = tempC;
        this.hfMw = hfMw;
        this.hfJgK = hfJgK;
    }
}
