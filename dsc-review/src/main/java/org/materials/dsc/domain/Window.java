package org.materials.dsc.domain;

/**
 * 用户在曲线上可拖动的区间（用原始点索引，单位基准随段一起携带）。
 * 空区间/退化区间是合法输入：判定为空并附诊断，而不是抛异常或静默忽略。
 */
public record Window(
        String id,
        String segmentId,
        int startIndex,
        int endIndex,
        String label,
        String createdAtIso) {

    public boolean isEmptyOrDegenerate() {
        return endIndex <= startIndex;
    }
}
