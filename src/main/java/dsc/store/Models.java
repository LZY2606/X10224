package dsc.store;

import dsc.core.*;
import java.util.List;

/** 投影层使用的领域记录。 */
public final class Models {
    private Models() {}

    public record Sample(String id, String name) {}

    /** 仪器输出段：原始表示原样保留（单位、点序列、方向、约定、气氛、速率）。 */
    public record Segment(String id, String sampleId, String label, int cycleIndex,
                          Direction direction, EndoConvention convention,
                          String atmosphere, Double rateKPerMin,
                          String tempUnit, String heatFlowUnit,
                          String rawFileRef, List<Point> points) {}

    /** 样品质量版本：每次修订产生新版本，旧版本保留。 */
    public record MassVersion(String id, String sampleId, long version, double massMg, String note) {}

    /** 温度标定记录。 */
    public record Calibration(String id, String sampleId, long version,
                              String reference, double offsetC, double slope) {}

    /** 用户选定的切线，与仪器输出分开存档。 */
    public record Tangent(String id, String segmentId, String side,
                          List<Point> points, String user) {}

    /** 基线候选：多个合理基线并列保留；adopted 只是指针，不删除其他候选。 */
    public record BaselineCandidate(String id, String segmentId, TransitionKind kind,
                                    BaselineType type, Baseline baseline,
                                    List<String> tangentIds, double windowLoC, double windowHiC,
                                    boolean adopted, boolean rejected) {}

    public record Diagnostic(String id, String code, String subject, String message, String severity) {}

    public record ConflictRec(String id, String subject, long baseSeq,
                              String eventA, String eventB, String status, String resolutionEvent) {}

    public record Decision(String id, String kind, String targetType, String targetId,
                           String note, String actor) {}
}
