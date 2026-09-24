package dsc.core;

import java.util.ArrayList;
import java.util.List;

/** 由曲线 + 基线计算 onset / peak / endset 与焓变，并收集误差来源。 */
public final class TransitionAnalyzer {

    /** 分析上下文：只放影响误差来源与符号的事实，不放展示层状态。 */
    public record Context(
            Direction direction,
            EndoConvention convention,
            double rateKPerMin,
            double massMg,
            long massVersion,
            double calibrationOffsetC,
            int parallelBaselineCount,
            boolean overlapsOtherTransition,
            int leftTangentPoints,
            int rightTangentPoints) {}

    private TransitionAnalyzer() {}

    public static AnalysisResult analyze(TransitionKind kind, Series series, Baseline baseline,
                                         double tLoC, double tHiC, Context ctx)
            throws EmptyIntervalException {
        List<ErrorSource> errors = new ArrayList<>();

        if (ctx.direction() == Direction.UNKNOWN) {
            errors.add(new ErrorSource(ErrorSource.UNKNOWN_DIRECTION,
                    "扫描方向未知：方向只能来自仪器元数据或速率符号，不能按文件名猜测", 0, ""));
        }
        if (series.isNonUniform(0.05)) {
            errors.add(new ErrorSource(ErrorSource.NON_UNIFORM_SPACING,
                    "温度非等间隔：按实际 ΔT 梯形积分，未重采样", 0, "°C"));
        }
        if (ctx.convention() == EndoConvention.EXO_UP) {
            errors.add(new ErrorSource(ErrorSource.REVERSED_CONVENTION,
                    "该段为放热朝上（EXO_UP）记录，符号已按约定翻转", 0, ""));
        }
        if (ctx.convention() == EndoConvention.UNKNOWN) {
            errors.add(new ErrorSource(ErrorSource.REVERSED_CONVENTION,
                    "吸放热约定未知：焓变符号不可唯一决定", 0, ""));
        }
        if (ctx.overlapsOtherTransition()) {
            errors.add(new ErrorSource(ErrorSource.OVERLAPPING_TRANSITION,
                    "与相邻转折窗口重叠：两条基线候选均保留，积分区间相交", 0, "°C"));
        }
        if (ctx.massVersion() > 1) {
            errors.add(new ErrorSource(ErrorSource.MASS_REVISION,
                    "样品质量第 " + ctx.massVersion() + " 版：归一化派生已按新版质量重算", 0, "mg"));
        }
        if (ctx.calibrationOffsetC() != 0) {
            errors.add(new ErrorSource(ErrorSource.CALIBRATION_OFFSET,
                    "温度标定偏移已应用于特征温度", ctx.calibrationOffsetC(), "°C"));
        }
        if (ctx.parallelBaselineCount() > 1) {
            errors.add(new ErrorSource(ErrorSource.BASELINE_AMBIGUITY,
                    "存在 " + ctx.parallelBaselineCount() + " 条合理基线候选，差异已保留", 0, ""));
        }
        if (ctx.leftTangentPoints() < 3 || ctx.rightTangentPoints() < 3) {
            errors.add(new ErrorSource(ErrorSource.TANGENT_SPARSE,
                    "切线点稀疏（左 " + ctx.leftTangentPoints() + " / 右 " + ctx.rightTangentPoints() + "）",
                    0, ""));
        }

        Series w = series.window(tLoC, tHiC);
        if (w.size() < 2) {
            throw new EmptyIntervalException("分析窗口 [" + tLoC + ", " + tHiC + "] °C 内数据点不足: " + w.size());
        }

        // 残差 = 信号 - 基线；统一为吸热方向后，峰方向由转折类型决定
        double sign = ctx.convention() == EndoConvention.EXO_UP ? -1.0 : 1.0;
        double peakAbs = -1;
        double peakT = Double.NaN;
        double peakResid = 0;
        for (Point p : w.points()) {
            double r = (p.hfMw() - baseline.at(p.tC())) * sign;
            // 放热（结晶）取负峰，吸热（熔融）取正峰；玻璃化取台阶中点
            double target = switch (kind) {
                case CRYSTALLIZATION -> -r;
                case MELTING -> r;
                case GLASS -> Math.abs(r);
            };
            if (target > peakAbs) {
                peakAbs = target;
                peakT = p.tC();
                peakResid = r;
            }
        }

        Double onset = null, endset = null;
        Double deltaH = null;
        if (kind == TransitionKind.GLASS) {
            // 玻璃化：台阶转变，onset/endset 取残差越过 5% 台阶高度的位置
            double threshold = peakAbs * 0.05;
            Double first = null, last = null;
            for (Point p : w.points()) {
                double r = Math.abs((p.hfMw() - baseline.at(p.tC())) * sign);
                if (r >= threshold) { if (first == null) first = p.tC(); last = p.tC(); }
            }
            onset = first;
            endset = last;
        } else {
            // 峰型转折：onset/endset 取残差越过峰值 2% 的边界
            double threshold = peakAbs * 0.02;
            Double first = null, last = null;
            for (Point p : w.points()) {
                double r = (p.hfMw() - baseline.at(p.tC())) * sign;
                double oriented = kind == TransitionKind.CRYSTALLIZATION ? -r : r;
                if (oriented >= threshold) { if (first == null) first = p.tC(); last = p.tC(); }
            }
            onset = first;
            endset = last;
            double areaMj = Integrator.integrateMj(series, baseline, tLoC, tHiC,
                    ctx.rateKPerMin(), ctx.convention());
            deltaH = Integrator.normalizeJPerG(areaMj, ctx.massMg());
        }

        // 温度标定偏移只作用于特征温度读数
        double off = ctx.calibrationOffsetC();
        return new AnalysisResult(kind,
                onset == null ? null : onset + off,
                Double.isNaN(peakT) ? null : peakT + off,
                endset == null ? null : endset + off,
                deltaH, List.copyOf(errors), null);
    }
}
