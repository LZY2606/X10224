package dsc.rule;

/** 转折区间（开尔文温度坐标）。必须落在单一程序段内。 */
public record FeatureWindow(String segmentId, double startK, double endK) {

    public boolean isEmptyAcross(java.util.List<XY> ascendingCurve) {
        for (XY p : ascendingCurve) {
            if (p.x() >= startK && p.x() <= endK) {
                return false;
            }
        }
        return true;
    }

    public double widthK() {
        return endK - startK;
    }
}
