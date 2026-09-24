package dsc.domain;

/**
 * 人工裁定。冲突的人工决定同时保存两份，解决产生新事件，
 * 未选基线/候选永不删除。
 *
 * @param id           稳定身份
 * @param sequence     顺序
 * @param author       审阅者
 * @param action       ACCEPT / REJECT / ADJUST
 * @param targetId     被裁定对象（intervalId / baselineCandidateId）
 * @param payloadJson  裁定内容
 * @param baseVersion  编辑所基于的修订号（乐观锁）
 * @param supersededBy 被哪个解决事件取代；未被取代为 null
 */
public record Decision(String id, long sequence, String author, String action,
                       String targetId, String payloadJson, long baseVersion,
                       Long supersededBy) {
}
