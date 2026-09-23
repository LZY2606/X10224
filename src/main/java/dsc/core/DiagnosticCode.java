package dsc.core;

/**
 * Stable diagnostic identities for the counter-example archive. Each rule carries
 * the rule version that created it; judgment text is emitted through {@link Diagnostic}.
 */
public enum DiagnosticCode {
    REVERSED_HEATFLOW(DiagnosticSeverity.WARNING,
            "反向热流：仪器声明的吸热方向与观测偏转不一致，已按声明约定归一化并翻转校正"),
    NON_UNIFORM_TEMPERATURE(DiagnosticSeverity.INFO,
            "非等间隔温度：采样温度间距不一致，积分改用梯形法"),
    OVERLAPPING_TRANSITION(DiagnosticSeverity.WARNING,
            "重叠转折：同一区段存在多个重叠区间，无法唯一切分，保留全部合法候选"),
    CYCLE_SWITCH(DiagnosticSeverity.ERROR,
            "循环段切换：区间跨越了不同循环/加热-冷却段边界，不允许跨段积分"),
    MASS_VERSION_STALE(DiagnosticSeverity.WARNING,
            "质量版本变更：所有质量归一化派生已过期，需要重算"),
    PARALLEL_BASELINES(DiagnosticSeverity.INFO,
            "并列基线：存在多个同样合理的基线，保留差异，等待人工裁定"),
    EMPTY_INTERVAL(DiagnosticSeverity.WARNING,
            "空区间：选定区间在该段曲线上没有数据点，无法积分"),
    DIRECTION_MISMATCH(DiagnosticSeverity.ERROR,
            "加热与冷却段不混用：分析请求引用了方向不一致的段"),
    UNKNOWN_CONVENTION(DiagnosticSeverity.ERROR,
            "吸放热方向未声明：不能靠文件名猜测，方向判据缺失"),
    UNCONVERTIBLE_UNIT(DiagnosticSeverity.WARNING,
            "无法转换的值保留在原位置并附诊断，不能为画图而丢弃"),
    NON_MONOTONIC_TEMPERATURE(DiagnosticSeverity.WARNING,
            "温度轴非单调，段方向与数据不一致"),
    CONCURRENT_EDIT(DiagnosticSeverity.WARNING,
            "并发编辑：基线版本与当前指针不一致，需要可解决的冲突"),
    RULE_MIGRATION(DiagnosticSeverity.INFO,
            "旧版本迁移：派生按新规则重算，旧快照保留"),
    WRITE_INTERRUPTED(DiagnosticSeverity.WARNING,
            "写入中断：未完成的版本被丢弃，状态回到上一完整版本");

    private final DiagnosticSeverity severity;
    private final String defaultMessage;

    DiagnosticCode(DiagnosticSeverity severity, String defaultMessage) {
        this.severity = severity;
        this.defaultMessage = defaultMessage;
    }

    public DiagnosticSeverity severity() {
        return severity;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
