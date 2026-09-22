package dsc.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * 分别存档的直接来源（仪器输出/质量/速率/气氛/温度标定/切线选择）。
 * rawText 原文在 blob 中单独保存；parsed 保留每个字段的原始表示。
 */
public record SourceRecord(
        String id,
        String sourceType,
        int version,
        String fileName,
        String mediaType,
        String sha256,
        JsonNode parsed,
        List<Diagnostic> diagnostics) {
}
