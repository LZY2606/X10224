package dsc.domain;

/**
 * 用户选定的切线锚点（温度坐标，K）。分别存档为独立直接来源；
 * 两侧锚点分别落在转折前后的近似线性区。
 */
public record TangentAnchors(String tangentId,
                             String segmentId,
                             double leftStartK,
                             double leftEndK,
                             double rightStartK,
                             double rightEndK) {

    public boolean valid() {
        return leftStartK < leftEndK
                && leftEndK < rightStartK
                && rightStartK < rightEndK;
    }
}
