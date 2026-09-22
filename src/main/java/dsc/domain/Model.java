package dsc.domain;

/** 领域枚举与常量。所有派生状态机在这里集中定义，避免散落的字符串。 */
public final class Model {
    private Model() {}

    /** 六类分别存档的来源。 */
    public enum SourceKind {
        INSTRUMENT_OUTPUT, SAMPLE_MASS, SCAN_RATE, ATMOSPHERE, TEMP_CALIBRATION, USER_TANGENT
    }

    public enum DerivedKind {
        SEGMENT, NORMALIZED_CURVE, BASELINE_CANDIDATE, TRANSITION
    }

    /** 行状态：BUILDING 行在提交前崩溃会被启动清理标记为 ABORTED。 */
    public enum RowStatus {
        BUILDING, COMPLETE, STALE, SUPERSEDED, ABORTED
    }

    public enum ScanDirection {
        HEATING, COOLING
    }

    /** 热流符号约定只能来自仪器元数据，不能来自文件名。 */
    public enum SignConvention {
        ENDO_UP, EXO_UP, UNKNOWN
    }

    public enum TransitionStatus {
        UNDECIDED, DECIDED, EMPTY_INTERVAL, DIRECTION_UNKNOWN
    }

    public enum PeakDirection {
        ENDOTHERMIC, EXOTHERMIC, UNKNOWN
    }

    /** 事件类型：所有判断依据都以机器可读事件落库。 */
    public static final class Ev {
        public static final String SOURCE_ARCHIVED = "SOURCE_ARCHIVED";
        public static final String SOURCE_REVISED = "SOURCE_REVISED";
        public static final String REVIEW_CONFIRMED = "REVIEW_CONFIRMED";
        public static final String DERIVED_STALE = "DERIVED_STALE";
        public static final String DERIVED_RECOMPUTED = "DERIVED_RECOMPUTED";
        public static final String DECISION = "DECISION";
        public static final String CONFLICT = "CONFLICT";
        public static final String CONFLICT_RESOLVED = "CONFLICT_RESOLVED";
        public static final String MASS_REVISION_INVALIDATES = "MASS_REVISION_INVALIDATES";
        public static final String DIRECTION_FROM_METADATA = "DIRECTION_FROM_METADATA";
        public static final String DIRECTION_UNDETERMINED = "DIRECTION_UNDETERMINED";
        public static final String DIAGNOSTIC = "DIAGNOSTIC";
        public static final String SCENARIO_STEP = "SCENARIO_STEP";
        public static final String IMPORT = "IMPORT";

        private Ev() {}
    }
}
