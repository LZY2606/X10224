package dsc.domain;

/** 焓变/温度的误差来源条目：显示时可回溯到具体来源节点。 */
public record ErrorSource(
        String sourceId,
        String sourceKind,            // mass / rate / calibration / baseline / sampling
        String message,
        double magnitudeJPerG
) {}
