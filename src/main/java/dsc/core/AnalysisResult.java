package dsc.core;

import java.util.List;

/**
 * 一次转折分析的派生结果。所有量携带单位；deps 记录依赖版本指纹，
 * 供依赖图判断“为何仍有效 / 为何需重算”。
 */
public record AnalysisResult(
        TransitionKind kind,
        Double onsetC, Double peakC, Double endsetC,
        Double deltaHJPerG,
        List<ErrorSource> errorSources,
        Deps deps) {

    /** 依赖版本指纹：任一输入变化都会改变对应字段。 */
    public record Deps(String segmentId, long segmentVersion,
                       String baselineId,
                       String massId, long massVersion,
                       String calibrationId, long calibrationVersion) {}
}
