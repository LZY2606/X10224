PRAGMA user_version = 2;
CREATE TABLE events (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  type TEXT NOT NULL,
  actor TEXT NOT NULL,
  branch TEXT NOT NULL DEFAULT 'main',
  base_seq INTEGER NOT NULL DEFAULT 0,
  target_id TEXT,
  run_id TEXT,
  feature_id TEXT,
  judge_code TEXT,
  summary TEXT,
  payload TEXT NOT NULL DEFAULT '{}',
  status TEXT NOT NULL DEFAULT 'applied',
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);
CREATE TABLE sources (
  id TEXT NOT NULL,
  sample_id TEXT NOT NULL,
  run_id TEXT,
  kind TEXT NOT NULL,
  version INTEGER NOT NULL,
  raw_text TEXT,
  fields TEXT,
  unit_context TEXT,
  diagnostics TEXT,
  rationale TEXT,
  event_seq INTEGER NOT NULL,
  PRIMARY KEY (id, version)
);
CREATE TABLE derivations (
  key TEXT NOT NULL,
  facet TEXT NOT NULL,
  version INTEGER NOT NULL,
  status TEXT NOT NULL,
  kind TEXT,
  event_seq INTEGER NOT NULL,
  dependency_versions TEXT,
  diagnostics TEXT,
  why TEXT,
  json TEXT,
  PRIMARY KEY (key, version)
);
CREATE TABLE pointers (
  scope TEXT NOT NULL,
  ref TEXT NOT NULL,
  key TEXT NOT NULL,
  current_version INTEGER NOT NULL,
  PRIMARY KEY (scope, ref, key)
);
CREATE TABLE snapshots (
  event_seq INTEGER NOT NULL,
  branch TEXT NOT NULL,
  label TEXT,
  json TEXT NOT NULL,
  PRIMARY KEY (event_seq, branch)
);
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
