package org.materials.dsc.domain;

/**
 * 用户选定的切线：按原始点索引给定左右切线窗口。切线是独立来源，
 * 不藏在基线参数里——换切线会触发以它为输入的候选重算。
 */
public record TangentSelection(
        String id,
        String segmentId,
        String createdBy,
        int leftFrom,
        int leftTo,
        int rightFrom,
        int rightTo,
        String createdAtIso) {
}
