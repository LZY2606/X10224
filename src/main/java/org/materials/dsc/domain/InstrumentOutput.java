package org.materials.dsc.domain;

import java.util.List;

/**
 * 仪器输出存档（直接来源之一）。包含：
 *  - 原始行（字面量保留）
 *  - 仪器声明的方向与热流符号约定（不从文件名推断）
 *  - 非等间隔标记与任何无法转换点的诊断
 */
public record InstrumentOutput(String id, String fileName, String instrument,
                               Direction declaredDirection, SignConvention signConvention,
                               String declaredDirectionRaw, String signConventionRaw,
                               Units tempUnit, Units hfUnit,
                               List<SampleRow> rows,
                               List<Diagnostic> diagnostics,
                               boolean nonUniformTemperature,
                               double medianStepCelsius,
                               String importEventSeq, String importedAt,
                               String sha256) {
    public int rowCount() {
        return rows.size();
    }
}
