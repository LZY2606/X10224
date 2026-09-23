package dsc.store;

/**
 * Database schema definitions and migration.
 *
 * <p>Derivations are written as independent immutable versions inside a
 * single transaction; the "current" pointer only moves when the whole
 * transaction commits. After a crash during writing the reader therefore
 * sees either the old version or the complete new one — never a partial row
 * set.
 */
public final class Schema {

    private Schema() {}

    public static final int CURRENT_VERSION = 2;

    /** Schema version 1 — shipped so the migration test has something old. */
    public static final String V1 = """
            CREATE TABLE meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
            CREATE TABLE study (
              id TEXT PRIMARY KEY,
              material_code TEXT NOT NULL,
              description TEXT NOT NULL,
              created_at TEXT NOT NULL,
              head_seq INTEGER NOT NULL DEFAULT 0,
              base_seq INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE source_record (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              study_id TEXT NOT NULL,
              type TEXT NOT NULL,
              supersedes_id TEXT,
              segment_id TEXT,
              unit TEXT,
              value REAL,
              raw_text TEXT,
              basis TEXT,
              recorded_at TEXT,
              recorded_by TEXT,
              retired INTEGER NOT NULL DEFAULT 0,
              created_seq INTEGER NOT NULL,
              PRIMARY KEY (id, version)
            );
            CREATE TABLE raw_point (
              source_id TEXT NOT NULL,
              version INTEGER NOT NULL,
              idx INTEGER NOT NULL,
              temp_value REAL,
              temp_unit TEXT,
              temp_raw TEXT,
              temp_convertible INTEGER NOT NULL,
              hf_value REAL,
              hf_unit TEXT,
              hf_raw TEXT,
              hf_convertible INTEGER NOT NULL,
              diagnostic TEXT,
              PRIMARY KEY (source_id, version, idx)
            );
            CREATE TABLE segment (
              id TEXT PRIMARY KEY,
              study_id TEXT NOT NULL,
              ord INTEGER NOT NULL,
              cycle TEXT NOT NULL,
              direction TEXT NOT NULL,
              direction_basis TEXT,
              trace_source_id TEXT,
              rate_source_id TEXT,
              atmosphere_source_id TEXT,
              calibration_source_id TEXT,
              t_start_k REAL,
              t_end_k REAL,
              point_count INTEGER,
              reversed_signal INTEGER,
              non_uniform INTEGER,
              created_seq INTEGER NOT NULL
            );
            CREATE TABLE segment_diag (
              segment_id TEXT NOT NULL,
              idx INTEGER NOT NULL,
              text TEXT NOT NULL,
              PRIMARY KEY (segment_id, idx)
            );
            CREATE TABLE baseline_candidate (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              study_id TEXT NOT NULL,
              segment_id TEXT NOT NULL,
              method TEXT NOT NULL,
              left_t_k REAL, right_t_k REAL, left_hf_w REAL, right_hf_w REAL,
              slope_w_per_k REAL, intercept_w REAL,
              status TEXT NOT NULL,
              decided_by TEXT,
              depends_on TEXT,
              created_seq INTEGER NOT NULL,
              decided_seq INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (id, version)
            );
            CREATE TABLE baseline_diag (
              baseline_id TEXT NOT NULL,
              version INTEGER NOT NULL,
              idx INTEGER NOT NULL,
              text TEXT NOT NULL,
              PRIMARY KEY (baseline_id, version, idx)
            );
            CREATE TABLE transition (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              study_id TEXT NOT NULL,
              segment_id TEXT NOT NULL,
              baseline_id TEXT NOT NULL,
              kind TEXT NOT NULL,
              onset_t_k REAL, peak_t_k REAL, endset_t_k REAL,
              enthalpy_jg REAL, enthalpy_error_jg REAL,
              depends_on TEXT,
              stale INTEGER NOT NULL DEFAULT 0,
              created_seq INTEGER NOT NULL,
              stale_seq INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY (id, version)
            );
            CREATE TABLE transition_error (
              transition_id TEXT NOT NULL,
              version INTEGER NOT NULL,
              idx INTEGER NOT NULL,
              code TEXT NOT NULL,
              description TEXT,
              contribution_jg REAL,
              source_id TEXT,
              PRIMARY KEY (transition_id, version, idx)
            );
            CREATE TABLE event_log (
              seq INTEGER PRIMARY KEY AUTOINCREMENT,
              study_id TEXT NOT NULL,
              type TEXT NOT NULL,
              actor TEXT,
              subject_id TEXT,
              rule_code TEXT,
              rationale TEXT,
              affected_ids TEXT,
              payload TEXT,
              at TEXT NOT NULL
            );
            CREATE TABLE current_pointer (
              study_id TEXT NOT NULL,
              kind TEXT NOT NULL,
              logical_id TEXT NOT NULL,
              version INTEGER NOT NULL,
              PRIMARY KEY (study_id, kind, logical_id)
            );
            """;

    /** V2 adds the dependency-edge table used by the propagation engine. */
    public static final String V2_ADD = """
            CREATE TABLE dep_edge (
              study_id TEXT NOT NULL,
              node_id TEXT NOT NULL,
              dependency_id TEXT NOT NULL,
              node_kind TEXT NOT NULL,
              PRIMARY KEY (study_id, node_id, dependency_id)
            );
            """;
}
