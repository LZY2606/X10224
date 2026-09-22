package dsc.domain;

import java.util.List;
import java.util.Map;

/**
 * 一条独立存档的直接来源。六种 {@link SourceKind} 各自独立、可独立改版。
 * {@code raw} 保存导入时的原始表示；派生层不得覆盖原始内容。
 */
public record SourceDoc(
        String id,
        String sampleId,
        String runId,
        SourceKind kind,
        int version,
        String rawText,
        Map<String, String> fields,
        String unitContext,
        List<String> diagnostics,
        String rationale,
        long eventSeq
) {
    public SourceDoc withVersion(int newVersion, long newEventSeq) {
        return new SourceDoc(id, sampleId, runId, kind, newVersion, rawText, fields,
                unitContext, diagnostics, rationale, newEventSeq);
    }
}
