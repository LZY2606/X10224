package dsc.domain;

/**
 * 用户为一个转折选定的切线/区间（可拖动区间的存档形式）。
 * 温度坐标保留原始输入表示，另存规范化 K。
 */
public record TangentSelection(
        String featureId,
        String baselineMethod,
        double leftT,
        double rightT,
        double onsetT,
        double endsetT,
        String rawLeft,
        String rawRight,
        String rationale
) {
}
