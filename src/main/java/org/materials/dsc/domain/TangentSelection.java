package org.materials.dsc.domain;

/**
 * 用户选定的切线窗口：[x1,x2] 为归一化温度(°C)上的闭区间端点。
 * 左右两段切线定义一条候选基线；人工选择只产生新版本，不覆盖被放弃的候选。
 */
public record TangentSelection(String id, String traceId, String segmentId,
                               double leftX1, double leftX2,
                               double rightX1, double rightX2,
                               String label, String note, String eventSeq, String at) {
}
