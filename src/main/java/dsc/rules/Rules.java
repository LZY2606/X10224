package dsc.rules;

/**
 * Machine-readable domain rules. Every judgement emitted by the engine carries one of
 * these codes plus the rationale, so reviews can trace why a value was accepted,
 * rejected, staled or kept as a candidate.
 */
public final class Rules {

    public static final String DIRECTION_FROM_METADATA = "DIRECTION_FROM_METADATA";
    public static final String DIRECTION_UNDECIDABLE = "DIRECTION_UNDECIDABLE";
    public static final String NO_SEGMENT_MIXING = "NO_SEGMENT_MIXING";
    public static final String PARALLEL_BASELINES_KEPT = "PARALLEL_BASELINES_KEPT";
    public static final String MASS_REVISION_STALES_NORMALIZED = "MASS_REVISION_STALES_NORMALIZED";
    public static final String LOCAL_REVISION_SCOPED = "LOCAL_REVISION_SCOPED";
    public static final String UNCONVERTIBLE_KEPT_RAW = "UNCONVERTIBLE_KEPT_RAW";
    public static final String EMPTY_INTERVAL = "EMPTY_INTERVAL";
    public static final String OVERLAPPING_TRANSITIONS = "OVERLAPPING_TRANSITIONS";
    public static final String NON_UNIFORM_GRID = "NON_UNIFORM_GRID";
    public static final String REVERSED_HEAT_FLOW = "REVERSED_HEAT_FLOW";

    public static String rationale(String code) {
        return switch (code) {
            case DIRECTION_FROM_METADATA ->
                "吸放热方向只能来自仪器元数据 heatFlowConvention，禁止从文件名猜测";
            case DIRECTION_UNDECIDABLE ->
                "缺少热流方向元数据，无法唯一决定，保留全部合法候选直至人工裁定";
            case NO_SEGMENT_MIXING ->
                "加热段与冷却段分别归一化、分别建立基线与积分，禁止混用";
            case PARALLEL_BASELINES_KEPT ->
                "多个合理基线全部保留差异，采纳其一不删除其他候选";
            case MASS_REVISION_STALES_NORMALIZED ->
                "质量修订后所有归一化派生及其下游（基线/积分/汇总）过期，需重算";
            case LOCAL_REVISION_SCOPED ->
                "仅影响局部依赖的源数据改动只使受影响派生过期，其余保持有效";
            case UNCONVERTIBLE_KEPT_RAW ->
                "无法转换的值留在原位置并附诊断，不为画图而丢弃";
            case EMPTY_INTERVAL ->
                "区间内无数据点（仪器空区间），不生成基线与积分值";
            case OVERLAPPING_TRANSITIONS ->
                "转折区间相互重叠，两个转折都保留并分别积分";
            case NON_UNIFORM_GRID ->
                "温度非等间隔，积分按实际网格梯形求积，不假定为等步长";
            case REVERSED_HEAT_FLOW ->
                "该段热流方向约定与规范基准相反，归一化时按元数据翻转符号";
            default -> code;
        };
    }

    private Rules() {}
}
