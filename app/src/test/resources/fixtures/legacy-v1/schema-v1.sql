-- Legacy DSC reviewer schema v1: derivations were not branch-scoped (no branch_id column).
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS sample (
  id TEXT PRIMARY KEY, code TEXT NOT NULL, material TEXT, note TEXT,
  created_at INTEGER NOT NULL, revision INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS segment (
  id TEXT PRIMARY KEY, sample_id TEXT NOT NULL, cycle_id TEXT, ord INTEGER NOT NULL,
  role TEXT NOT NULL, direction TEXT NOT NULL, sign_convention TEXT NOT NULL,
  t_unit TEXT, hf_unit TEXT, t_start_raw REAL, t_end_raw REAL, point_count INTEGER NOT NULL,
  instrument_output_id TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS curve_point (
  segment_id TEXT NOT NULL, idx INTEGER NOT NULL, t_raw REAL NOT NULL, t_raw_unit TEXT,
  hf_raw REAL NOT NULL, hf_raw_unit TEXT, t_c REAL, hf_wg REAL, convertible INTEGER NOT NULL,
  PRIMARY KEY (segment_id, idx)
);
CREATE TABLE IF NOT EXISTS source_record (
  id TEXT NOT NULL, version INTEGER NOT NULL, sample_id TEXT NOT NULL, segment_id TEXT,
  kind TEXT NOT NULL, is_current INTEGER NOT NULL, raw_text TEXT, unit TEXT, numeric_value REAL,
  numeric_valid INTEGER NOT NULL, payload_json TEXT, origin_ref TEXT, created_by TEXT,
  supersedes_id TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (id, version)
);
CREATE TABLE IF NOT EXISTS interval_tbl (
  id TEXT NOT NULL, version INTEGER NOT NULL, sample_id TEXT NOT NULL,
  segment_id TEXT NOT NULL, is_current INTEGER NOT NULL, t_start_c REAL, t_end_c REAL,
  t_start_raw REAL, t_end_raw REAL, raw_unit TEXT, label TEXT, created_by TEXT, created_at INTEGER NOT NULL,
  PRIMARY KEY (id, version)
);
CREATE TABLE IF NOT EXISTS ruleset (
  version INTEGER PRIMARY KEY, is_current INTEGER NOT NULL, smoothing_window_c REAL,
  peak_prominence_wg REAL, glass_step_min_wg REAL, sign_policy TEXT, created_by TEXT,
  created_at INTEGER NOT NULL, note TEXT
);
CREATE TABLE IF NOT EXISTS derivation (
  id TEXT NOT NULL, version INTEGER NOT NULL, sample_id TEXT NOT NULL,
  kind TEXT NOT NULL, segment_id TEXT, interval_id TEXT, is_current INTEGER NOT NULL,
  status TEXT NOT NULL, method TEXT, candidate_status TEXT NOT NULL, basis_json TEXT, message TEXT,
  created_by TEXT, created_at INTEGER NOT NULL, PRIMARY KEY (id, version)
);
CREATE TABLE IF NOT EXISTS derivation_dep (
  derivation_id TEXT NOT NULL, derivation_version INTEGER NOT NULL, ref_type TEXT NOT NULL,
  ref_id TEXT NOT NULL, ref_version INTEGER NOT NULL,
  PRIMARY KEY (derivation_id, derivation_version, ref_type, ref_id)
);
CREATE TABLE IF NOT EXISTS diagnostic (
  id TEXT PRIMARY KEY, derivation_id TEXT NOT NULL, derivation_version INTEGER NOT NULL,
  segment_id TEXT, interval_id TEXT, rule TEXT NOT NULL, severity TEXT NOT NULL,
  verdict TEXT NOT NULL, message TEXT NOT NULL, evidence_json TEXT, created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS event_log (
  seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE, branch_id TEXT NOT NULL DEFAULT 'main',
  parent_event_id TEXT, kind TEXT NOT NULL, subject_type TEXT, subject_id TEXT, subject_version INTEGER,
  actor TEXT, detail_json TEXT, note TEXT, created_at INTEGER NOT NULL
);
