package org.materials.dsc.domain;

import java.util.List;

/**
 * 一个合理基线候选。多个合理候选同时保留，差异不被吞掉。
 * baselineY[i] 与积分窗口内规范化热流逐点对齐（mW/g）。
 */
public record BaselineCandidate(
        String id,
        String windowId,
        String modelKey,
        String modelLabel,
        List<Double> baselineY,
        double residualRmsMilliwattPerGram,
        List<String> diagnostics,
        String status) {

    public boolean accepted() {
        return "accepted".equals(status);
    }

    public boolean rejected() {
        return "rejected".equals(status);
    }
}
