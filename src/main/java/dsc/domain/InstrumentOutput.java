package dsc.domain;

import java.util.List;

/**
 * 仪器输出归档：原始 CSV 文本逐字保存，外加从文件头解析的元数据。
 * 仪器头是吸放热方向的唯一合法来源（禁止文件名猜测）。
 */
public final class InstrumentOutput {
    public final String outputId;
    public final String fileName;
    public final String instrumentModel;
    public final String directionHeader;   // 头中的方向标记原文
    public final String tempUnit;
    public final String heatFlowUnit;
    public final String rateRaw;
    public final String atmosphereLabel;
    public final String calibrationLabel;
    public final String rawCsv;
    public final List<String[]> rows;      // 已拆列的原始行（诊断用）

    public InstrumentOutput(String outputId, String fileName, String instrumentModel, String directionHeader,
                            String tempUnit, String heatFlowUnit, String rateRaw, String atmosphereLabel,
                            String calibrationLabel, String rawCsv, List<String[]> rows) {
        this.outputId = outputId;
        this.fileName = fileName;
        this.instrumentModel = instrumentModel;
        this.directionHeader = directionHeader;
        this.tempUnit = tempUnit;
        this.heatFlowUnit = heatFlowUnit;
        this.rateRaw = rateRaw;
        this.atmosphereLabel = atmosphereLabel;
        this.calibrationLabel = calibrationLabel;
        this.rawCsv = rawCsv;
        this.rows = List.copyOf(rows);
    }
}
