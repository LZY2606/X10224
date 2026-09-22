package dsc.domain;

/**
 * 温度标定来源文档。offsetK 为加到温度上的标定修正；标定修订只影响引用段的
 * 温度坐标派生（onset/peak/endset 温度），不影响与质量相关的焓归一化。
 */
public final class CalibrationRecord {
    public final String calibrationId;
    public final String standard;
    public final double offsetK;
    public final String rawText;
    public final String recordedAt;

    public CalibrationRecord(String calibrationId, String standard, double offsetK,
                             String rawText, String recordedAt) {
        this.calibrationId = calibrationId;
        this.standard = standard;
        this.offsetK = offsetK;
        this.rawText = rawText;
        this.recordedAt = recordedAt;
    }
}
