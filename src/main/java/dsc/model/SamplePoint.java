package dsc.model;

/**
 * 单个采样点。tempRaw/hfRaw 保留导入原文；
 * hfMw 是仪器原始热流（mW），归一化（除以质量）只发生在派生层。
 * 温度与热流无法转换的点保留原值并附诊断，不为画图丢弃。
 */
public record SamplePoint(
        int index,
        String tempRaw,
        String hfRaw,
        double tempC,
        double hfMw,
        boolean tempOk,
        boolean hfOk
) {}
