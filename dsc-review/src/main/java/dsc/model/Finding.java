package dsc.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个搜索窗口上的单条候选结果：基线 + 扣除基线后的峰/阶跃，
 * 含 onset/peak/endset、焓变与误差来源。
 */
public final class Finding {
    public String findingId;             // 稳定身份（跨重算保留，若窗口键不变）
    public String segmentId;
    public String windowId;
    public String candidateId;
    public BaselineModel baselineModel;
    public TransitionKind kind = TransitionKind.UNKNOWN;
    public String thermalSense;          // endothermic | exothermic
    public List<FeaturePoint> points = new ArrayList<>();
    public Double enthalpyJg;            // 符号化：吸热正、放热负
    public Double enthalpyJ;
    public Double deltaCpJgK;            // 玻璃化阶跃
    public double overlapScore;         // 与其他窗口重叠程度 0..1
    public final List<ErrorSource> errors = new ArrayList<>();
    public String status = "proposed";   // proposed | accepted | rejected | superseded
    public String decidedByEvent;
    public String note;

    public Finding() {}

    public FeaturePoint point(String role) {
        for (FeaturePoint p : points) if (p.role.equals(role)) return p;
        return null;
    }
}
