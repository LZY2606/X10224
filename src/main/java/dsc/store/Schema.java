package dsc.store;

/** SQLite schema. Identity is stored explicitly and preserved by export/import. */
public final class Schema {
    private Schema() {}

    public static final int LATEST_REVISION = 2;

    public static final String DDLS = """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sample (
              id TEXT PRIMARY KEY,
              name TEXT NOT NULL,
              material_id TEXT NOT NULL,
              ord INTEGER NOT NULL,
              attributes_json TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS mass_version (
              id TEXT PRIMARY KEY,
              sample_id TEXT NOT NULL REFERENCES sample(id),
              ord INTEGER NOT NULL,
              value REAL NOT NULL,
              unit TEXT NOT NULL,
              raw TEXT NOT NULL,
              reason TEXT NOT NULL,
              effective_from_event_id TEXT,
              UNIQUE(sample_id, ord)
            );

            CREATE TABLE IF NOT EXISTS run_info (
              id TEXT PRIMARY KEY,
              sample_id TEXT NOT NULL REFERENCES sample(id),
              instrument_serial TEXT NOT NULL,
              file_name TEXT NOT NULL,
              cycle_label TEXT NOT NULL,
              seq_index INTEGER NOT NULL,
              scan_rate_value REAL, scan_rate_unit TEXT, scan_rate_raw TEXT,
              atmosphere TEXT,
              flow_value REAL, flow_unit TEXT, flow_raw TEXT,
              cal_standard TEXT, cal_ref_v REAL, cal_ref_u TEXT,
              cal_obs_v REAL, cal_obs_u TEXT,
              cal_off_v REAL, cal_off_u TEXT, cal_certified_at TEXT,
              declared_sign TEXT NOT NULL,
              declared_direction TEXT NOT NULL,
              imported_event_id TEXT,
              UNIQUE(sample_id, seq_index)
            );

            CREATE TABLE IF NOT EXISTS curve_point (
              run_id TEXT NOT NULL REFERENCES run_info(id),
              ord INTEGER NOT NULL,
              time_raw REAL NOT NULL, time_unit TEXT NOT NULL,
              temp_raw REAL NOT NULL, temp_unit TEXT NOT NULL,
              hf_raw REAL NOT NULL, hf_unit TEXT NOT NULL,
              flagged INTEGER NOT NULL,
              PRIMARY KEY(run_id, ord)
            );

            CREATE TABLE IF NOT EXISTS segment (
              id TEXT PRIMARY KEY,
              run_id TEXT NOT NULL REFERENCES run_info(id),
              seq INTEGER NOT NULL,
              detected_direction TEXT NOT NULL,
              declared_direction TEXT NOT NULL,
              start_t_raw REAL, end_t_raw REAL, temp_unit TEXT,
              from_ord INTEGER NOT NULL,
              to_ord INTEGER NOT NULL,
              direction_consistent INTEGER NOT NULL,
              UNIQUE(run_id, seq)
            );

            CREATE TABLE IF NOT EXISTS transition (
              id TEXT PRIMARY KEY,
              run_id TEXT NOT NULL REFERENCES run_info(id),
              segment_id TEXT NOT NULL REFERENCES segment(id),
              ord INTEGER NOT NULL,
              label TEXT NOT NULL,
              kind_hint TEXT NOT NULL,
              region_from_ord INTEGER NOT NULL,
              region_to_ord INTEGER NOT NULL,
              segment_direction TEXT NOT NULL,
              UNIQUE(run_id, ord)
            );

            CREATE TABLE IF NOT EXISTS tangent_selection (
              id TEXT PRIMARY KEY,
              transition_id TEXT NOT NULL REFERENCES transition(id),
              version INTEGER NOT NULL,
              left_from_ord INTEGER NOT NULL,
              left_to_ord INTEGER NOT NULL,
              right_from_ord INTEGER NOT NULL,
              right_to_ord INTEGER NOT NULL,
              author TEXT, comment TEXT, event_id TEXT,
              UNIQUE(transition_id, version)
            );

            CREATE TABLE IF NOT EXISTS derived_version (
              id TEXT PRIMARY KEY,
              scope_key TEXT NOT NULL,
              number INTEGER NOT NULL,
              rule_version TEXT NOT NULL,
              produced_by_event_id TEXT,
              status TEXT NOT NULL,
              parent_version_id TEXT,
              input_hash_ids_json TEXT NOT NULL,
              created_at TEXT NOT NULL,
              UNIQUE(scope_key, number)
            );

            CREATE TABLE IF NOT EXISTS baseline_candidate (
              id TEXT PRIMARY KEY,
              transition_id TEXT NOT NULL REFERENCES transition(id),
              derived_version_id TEXT NOT NULL REFERENCES derived_version(id),
              rank INTEGER NOT NULL,
              method TEXT NOT NULL,
              accepted INTEGER NOT NULL,
              points_json TEXT NOT NULL,
              fit_rms REAL NOT NULL,
              notes_json TEXT NOT NULL,
              event_id TEXT,
              UNIQUE(derived_version_id, transition_id, rank)
            );

            CREATE TABLE IF NOT EXISTS analysis_result (
              id TEXT PRIMARY KEY,
              transition_id TEXT NOT NULL REFERENCES transition(id),
              baseline_candidate_id TEXT NOT NULL REFERENCES baseline_candidate(id),
              derived_version_id TEXT NOT NULL REFERENCES derived_version(id),
              onset_value REAL, onset_unit TEXT,
              peak_value REAL, peak_unit TEXT,
              endset_value REAL, endset_unit TEXT,
              enthalpy_value REAL, enthalpy_unit TEXT,
              endothermic_sign INTEGER,
              error_budget_json TEXT,
              status TEXT NOT NULL,
              diagnostics_json TEXT
            );

            CREATE TABLE IF NOT EXISTS decision (
              id TEXT PRIMARY KEY,
              transition_id TEXT NOT NULL,
              event_id TEXT,
              chosen_candidate_id TEXT,
              rejected_candidate_ids TEXT,
              action TEXT NOT NULL,
              author TEXT,
              based_on_version_id TEXT,
              conflicts_with_decision_id TEXT,
              resolution TEXT,
              comment TEXT
            );

            CREATE TABLE IF NOT EXISTS ledger_event (
              id TEXT PRIMARY KEY,
              seq INTEGER NOT NULL UNIQUE,
              type TEXT NOT NULL,
              actor TEXT,
              created_at TEXT NOT NULL,
              case_ref TEXT,
              rule_version TEXT,
              derived_version_id TEXT,
              refs_json TEXT NOT NULL,
              payload_json TEXT
            );

            CREATE TABLE IF NOT EXISTS current_pointer (
              scope_key TEXT PRIMARY KEY,
              derived_version_id TEXT NOT NULL REFERENCES derived_version(id)
            );

            CREATE TABLE IF NOT EXISTS dependency_edge (
              node_id TEXT NOT NULL,
              node_kind TEXT NOT NULL,
              dep_id TEXT NOT NULL,
              dep_kind TEXT NOT NULL,
              PRIMARY KEY(node_id, dep_id)
            );

            CREATE TABLE IF NOT EXISTS diagnostic (
              target_id TEXT NOT NULL,
              target_kind TEXT NOT NULL,
              code TEXT NOT NULL,
              message TEXT NOT NULL,
              at_coordinate TEXT,
              ord INTEGER NOT NULL,
              PRIMARY KEY(target_id, ord)
            );

            CREATE TABLE IF NOT EXISTS changeset (
              id TEXT PRIMARY KEY,
              started_at TEXT NOT NULL,
              status TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS changeset_op (
              changeset_id TEXT NOT NULL REFERENCES changeset(id),
              seq INTEGER NOT NULL,
              op_type TEXT NOT NULL,
              table_name TEXT NOT NULL,
              row_key TEXT NOT NULL,
              payload_json TEXT NOT NULL,
              PRIMARY KEY(changeset_id, seq)
            );
            """;
}
