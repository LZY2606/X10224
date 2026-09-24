package dsc.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Map;

/**
 * 追加日志事件。事件不可变；所有事实（含未采纳候选与判定依据）都以事件留存。
 * baseSeq: 编辑者所基于的事件序号，用于并发保存冲突检测。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "t")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Event.Simple.class, name = "simple"),
        @JsonSubTypes.Type(value = Event.RuleViolation.class, name = "rule"),
        @JsonSubTypes.Type(value = Event.SegmentImported.class, name = "seg"),
        @JsonSubTypes.Type(value = Event.MassRecorded.class, name = "mass"),
        @JsonSubTypes.Type(value = Event.TangentSelected.class, name = "tangent"),
        @JsonSubTypes.Type(value = Event.WindowChanged.class, name = "window"),
        @JsonSubTypes.Type(value = Event.BaselineProposed.class, name = "blp"),
        @JsonSubTypes.Type(value = Event.DecisionMade.class, name = "dec"),
        @JsonSubTypes.Type(value = Event.SourceRevised.class, name = "rev"),
        @JsonSubTypes.Type(value = Event.ConflictResolved.class, name = "conf"),
})
public sealed interface Event permits Event.Simple, Event.RuleViolation, Event.SegmentImported,
        Event.MassRecorded, Event.TangentSelected, Event.WindowChanged, Event.BaselineProposed,
        Event.DecisionMade, Event.SourceRevised, Event.ConflictResolved {

    long seq();
    String caseId();
    EventType type();
    String actor();
    long baseSeq();
    long clock();
    String message();

    /** 通用简单事件（开档/气氛/标定/异常/重放步骤/备注等）。 */
    record Simple(
            long seq, String caseId, EventType type, String actor, long baseSeq, long clock,
            String message, String subjectId, Map<String, Object> data
    ) implements Event {}

    /** 语义判定依据（机器可读）。 */
    record RuleViolation(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String ruleCode, String severity, String message,
            String affectedKind, String affectedId, Map<String, Object> evidence
    ) implements Event {
        @Override public EventType type() { return EventType.RULE_VIOLATION; }
    }

    record SegmentImported(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String segmentId, String rawDataId, int cycle, String instrument,
            String declaredDirection, String signConvention,
            String rateRaw, String rateUnit,
            String message
    ) implements Event {
        @Override public EventType type() { return EventType.SEGMENT_IMPORTED; }
    }

    record MassRecorded(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String massVersionId, int massSeq, String massRaw, String massUnit,
            String reason, String supersedesId, String message
    ) implements Event {
        @Override public EventType type() { return EventType.MASS_RECORDED; }
    }

    record TangentSelected(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String tangentId, String segmentId,
            double preLeftC, double preRightC, double postLeftC, double postRightC,
            String message
    ) implements Event {
        @Override public EventType type() { return EventType.TANGENT_SELECTED; }
    }

    record WindowChanged(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String windowId, String segmentId, int version,
            double fromC, double toC, boolean created, String message
    ) implements Event {
        @Override public EventType type() {
            return created ? EventType.WINDOW_CREATED : EventType.WINDOW_MOVED;
        }
    }

    record BaselineProposed(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String candidateId, String windowId, String method,
            boolean admissible, String message
    ) implements Event {
        @Override public EventType type() { return EventType.BASELINE_PROPOSED; }
    }

    record DecisionMade(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String windowId, String candidateId, String action,   // accept / reject / adjust
            String comment, String message
    ) implements Event {
        @Override public EventType type() { return EventType.DECISION_MADE; }
    }

    /** 只改动一个直接来源（如某个采样点 / 速率原始值），局部依赖失效。 */
    record SourceRevised(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String sourceKind, String sourceId, String field,
            String oldRaw, String newRaw, String message
    ) implements Event {
        @Override public EventType type() { return EventType.SOURCE_REVISED; }
    }

    record ConflictResolved(
            long seq, String caseId, String actor, long baseSeq, long clock,
            String conflictId, String resolution,   // keep_yours / take_theirs / merge
            String retainedEventIds, String message
    ) implements Event {
        @Override public EventType type() { return EventType.CONFLICT_RESOLVED; }
    }
}
