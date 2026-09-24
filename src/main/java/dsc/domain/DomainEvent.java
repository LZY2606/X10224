package dsc.domain;

/**
 * 机器可读取的判定事件。事件只追加；回到旧事件时按其引用的
 * 依赖版本快照恢复当时结论，绝不用最新计算覆盖历史。
 *
 * @param id           稳定身份
 * @param sequence     时间线顺序
 * @param type         事件类型（见 rules/engine 中定义的代码）
 * @param summary      人读判定依据
 * @param payloadJson  结构化判定（被采纳/未采纳候选、来源、影响范围）
 * @param depSnapshotJson 该事件时刻的依赖版本快照
 */
public record DomainEvent(String id, long sequence, String type, String summary,
                          String payloadJson, String depSnapshotJson) {
}
