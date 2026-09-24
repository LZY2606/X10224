package dsc.domain;

/**
 * 一次推导的独立版本产物。
 *
 * @param id          稳定身份 = kind:targetId（同目标多版本共享 id，版本号区分）
 * @param version     版本号，从 1 开始，只追加
 * @param kind        产物类型
 * @param targetId    segmentId / intervalId / transitionId
 * @param status      OK / STALE / INVALID
 * @param contentJson 计算结果（含单位、坐标基准、候选、诊断、误差来源）
 * @param depsHash    所依赖的来源+规则版本指纹，解释"为何仍有效/需重算"
 * @param ruleVersion 产生此版本的规则版本
 * @param sequence    写入顺序
 */
public record Artifact(String id, int version, ArtifactKind kind, String targetId,
                       String status, String contentJson, String depsHash,
                       String ruleVersion, long sequence) {
}
