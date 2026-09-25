package dsc.store;

/**
 * SQLite schema 定义与版本迁移。
 *
 * 设计原则：
 *  - 所有直接来源（仪器输出、质量、扫描速率、气氛、温度标定、切线）独立存档、独立版本。
 *  - 所有派生（校准序列、基线候选、转折特征）独立版本，只在完整成功后切换 current 指针。
 *  - 全部接受/拒绝/调整均写事件日志；事件只追加，永不更新。
 *  - 派生依赖以边表记录，支持依赖图传播与来源链回溯。
 */
final class Schema {

    static final int CURRENT_VERSION = 2;

    private Schema() {
    }

    static final String[] V1_DDL = {
            """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS sample_history (
              history_id TEXT PRIMARY KEY,
              label TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
            """,
            // 直接来源的统一版本表。kind 决定 payload 形态；raw_payload 保留原始表示。
            """
            CREATE TABLE IF NOT EXISTS source_version (
              source_uid TEXT NOT NULL,
              version INTEGER NOT NULL,
              kind TEXT NOT NULL,
              role TEXT NOT NULL,
              history_id TEXT NOT NULL,
              raw_payload TEXT NOT NULL,
              canonical_json TEXT NOT NULL,
              parent_uid TEXT,
              created_event_id INTEGER,
              created_at TEXT NOT NULL,
              PRIMARY KEY (source_uid, version)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS source_current (
              role TEXT NOT NULL,
              history_id TEXT NOT NULL,
              source_uid TEXT NOT NULL,
              version INTEGER NOT NULL,
              PRIMARY KEY (role, history_id)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS derived_version (
              derived_uid TEXT NOT NULL,
              version INTEGER NOT NULL,
              kind TEXT NOT NULL,
              history_id TEXT NOT NULL,
              payload_json TEXT NOT NULL,
              status TEXT NOT NULL,
              basis_hash TEXT NOT NULL,
              created_event_id INTEGER,
              created_at TEXT NOT NULL,
              stale_reason TEXT,
              PRIMARY KEY (derived_uid, version)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS derived_current (
              derived_uid TEXT PRIMARY KEY,
              version INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS dependency_edge (
              derived_uid TEXT NOT NULL,
              derived_version INTEGER NOT NULL,
              dep_role TEXT NOT NULL,
              dep_source_uid TEXT NOT NULL,
              dep_version INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS event_log (
              event_id INTEGER PRIMARY KEY AUTOINCREMENT,
              history_id TEXT NOT NULL,
              event_type TEXT NOT NULL,
              actor TEXT NOT NULL,
              created_at TEXT NOT NULL,
              payload_json TEXT NOT NULL,
              parent_event_id INTEGER
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS decision (
              decision_id TEXT PRIMARY KEY,
              history_id TEXT NOT NULL,
              event_id INTEGER NOT NULL,
              subject_uid TEXT NOT NULL,
              disposition TEXT NOT NULL,
              rejected_candidate_ids TEXT NOT NULL,
              rationale TEXT
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS edit_fence (
              history_id TEXT PRIMARY KEY,
              last_event_id INTEGER NOT NULL,
              editor TEXT NOT NULL
            )
            """
    };

    static final String[] V2_DDL = {
            // 诊断：无法转换/未参与派生的值留在原位并挂诊断，不为画图丢弃。
            """
            CREATE TABLE IF NOT EXISTS diagnostic_record (
              diagnostic_id INTEGER PRIMARY KEY AUTOINCREMENT,
              history_id TEXT NOT NULL,
              event_id INTEGER,
              subject_uid TEXT,
              severity TEXT NOT NULL,
              code TEXT NOT NULL,
              message TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS replay_marker (
              history_id TEXT NOT NULL,
              scenario_code TEXT NOT NULL,
              event_id INTEGER NOT NULL,
              PRIMARY KEY (history_id, scenario_code)
            )
            """
    };
}
