package dsc.domain;

/**
 * 不可变的直接来源存档。
 *
 * @param id          稳定身份（由案例包/内容决定，删除后重建仍相同）
 * @param kind        六类来源之一
 * @param refId       归属对象（segmentId / sampleId / transitionId）
 * @param version     同一 refId+kind 下的版本号，从 1 开始；修订只追加新版本
 * @param originalRep 原始表示（文件名字符串、表头、用户输入原文，规范化前）
 * @param payloadJson 结构化内容（可含无法换算的原值与诊断）
 * @param sequence    全局写入顺序号，决定导入后的顺序关系
 */
public record SourceRecord(String id, SourceKind kind, String refId, int version,
                           String originalRep, String payloadJson, long sequence) {
}
