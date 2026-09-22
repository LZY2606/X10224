package dsc.engine;

import dsc.domain.SourceDoc;

import java.util.List;

/**
 * 一次 run 重算时的来源快照：六类来源的当前版本。
 * 任何一个改版都改变依赖图版本，只传播到受影响的派生。
 */
public record AnalysisContext(
        SourceDoc instrument,
        SourceDoc mass,
        SourceDoc rate,
        SourceDoc atmosphere,
        SourceDoc calibration,
        SourceDoc tangent,
        List<String> diagnostics
) {
}
