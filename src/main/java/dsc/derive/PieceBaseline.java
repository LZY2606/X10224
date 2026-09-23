package dsc.derive;

import dsc.model.BaselineFit;
import dsc.model.Types;

/** Piecewise tangent baseline: pre tangent joined to post tangent at {@code joinK}. */
public final class PieceBaseline extends BaselineFit {
    public final double preA;
    public final double preB;
    public final double postA;
    public final double postB;
    public final double joinK;

    public PieceBaseline(String id, String intervalId, double preA, double preB,
                         double postA, double postB, double joinK,
                         Types.Decision decision, String decidedByEventId,
                         String rationale, int version) {
        super(id, intervalId, Kind.PIECEWISE_TANGENT, preA, preB, 0.0, Double.NaN,
                decision, decidedByEventId, rationale, version);
        this.preA = preA;
        this.preB = preB;
        this.postA = postA;
        this.postB = postB;
        this.joinK = joinK;
    }

    @Override
    public double valueAt(double tempK) {
        return tempK < joinK ? preA + preB * tempK : postA + postB * tempK;
    }
}
