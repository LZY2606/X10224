package dsc.model;

/** 一条基线候选：模型 + 参数，差异可量化比较。 */
public final class CandidateBaseline {
    public String candidateId;
    public String windowId;
    public String segmentId;
    public BaselineModel model;
    public double[] params;               // 模型参数
    public double leftTempC;
    public double rightTempC;
    public boolean fromUserTangent;
    public double anchorRms;              // 锚点区间拟合残差
    public String rationale;

    public CandidateBaseline() {}

    public double valueAt(double t) {
        switch (model) {
            case LINEAR:
                return params[0] + params[1] * t;
            case SIGMOID: {
                double lo = params[0], hi = params[1], mid = params[2], width = Math.max(params[3], 1e-9);
                double f = 1.0 / (1.0 + Math.exp(-(t - mid) / width));
                return lo + (hi - lo) * f;
            }
            case PIECEWISE: {
                // params: [a0,b0, joinT, a1,b1]
                if (t < params[2]) return params[0] + params[1] * t;
                return params[3] + params[4] * t;
            }
            default:
                throw new IllegalStateException();
        }
    }
}
