package dsc.core;

/**
 * 峰积分。关键语义：
 *  - 非等间隔温度：直接按实际 ΔT 梯形积分，不重采样、不丢点。
 *  - 加热/冷却不混用：符号由带符号扫描速率决定（dt = dT/rate），冷却段 rate<0。
 *  - 反向热流：吸放热方向由 EndoConvention 决定，与文件名无关。
 */
public final class Integrator {

    private Integrator() {}

    /**
     * @param series      原始段（任意温度顺序）
     * @param baseline    已选基线
     * @param tLoC        积分下限（°C）
     * @param tHiC        积分上限（°C）
     * @param rateKPerMin 带符号扫描速率（加热>0，冷却<0）
     * @param convention  吸放热方向约定
     * @return 未归一化焓，单位 mJ（吸热为正，已按约定统一符号）
     */
    public static double integrateMj(Series series, Baseline baseline, double tLoC, double tHiC,
                                     double rateKPerMin, EndoConvention convention)
            throws EmptyIntervalException {
        if (rateKPerMin == 0) throw new EmptyIntervalException("扫描速率为 0，无法换算时间轴");
        Series w = series.window(tLoC, tHiC);
        if (w.size() < 2) {
            throw new EmptyIntervalException("积分窗口 [" + tLoC + ", " + tHiC + "] °C 内数据点不足: " + w.size());
        }
        // 梯形积分：∫(hf - base) dT，逐步使用真实 ΔT（兼容非等间隔与降序）
        double areaMwK = 0;
        for (int i = 1; i < w.size(); i++) {
            Point p0 = w.points().get(i - 1);
            Point p1 = w.points().get(i);
            double r0 = p0.hfMw() - baseline.at(p0.tC());
            double r1 = p1.hfMw() - baseline.at(p1.tC());
            areaMwK += (r0 + r1) / 2.0 * (p1.tC() - p0.tC());
        }
        // dT/dt = rate -> dt = dT/rate；rate 单位 K/min，换算到秒
        double areaMj = areaMwK / (rateKPerMin / 60.0);
        // 统一为“吸热为正”：EXO_UP 约定下信号符号相反
        if (convention == EndoConvention.EXO_UP) areaMj = -areaMj;
        return areaMj;
    }

    /** 归一化：mJ / mg = J/g。质量版本变更后所有归一化派生必须过期重算。 */
    public static double normalizeJPerG(double areaMj, double massMg) {
        if (massMg <= 0) throw new IllegalArgumentException("样品质量必须为正: " + massMg);
        return areaMj / massMg;
    }
}
