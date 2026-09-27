-- Legacy v2 schema used by migration tests (adds branches + pointers; no intervals/marks).
CREATE TABLE IF NOT EXISTS projects (
    project_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL, created_at_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sources (
    source_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, kind TEXT NOT NULL,
    filename TEXT NOT NULL, raw_payload TEXT NOT NULL, raw_fields_json TEXT NOT NULL,
    revises_source_id TEXT, ingest_seq INTEGER NOT NULL, branch_root TEXT NOT NULL,
    actor TEXT NOT NULL, created_at_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS events (
    seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE,
    project_id TEXT NOT NULL, parent_event_id TEXT, branch_root TEXT NOT NULL,
    type TEXT NOT NULL, actor TEXT NOT NULL, created_at_ms INTEGER NOT NULL,
    expected_base_seq INTEGER NOT NULL, payload_json TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS branches (
    branch_root TEXT PRIMARY KEY, project_id TEXT NOT NULL, label TEXT NOT NULL,
    tip_event_id TEXT NOT NULL, tip_seq INTEGER NOT NULL, status TEXT NOT NULL,
    forked_from_event_id TEXT
);
CREATE TABLE IF NOT EXISTS derivations (
    derivation_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, kind TEXT NOT NULL,
    interval_id TEXT, status TEXT NOT NULL, rule_version TEXT NOT NULL,
    inputs_json TEXT NOT NULL, payload_json TEXT NOT NULL, error_code TEXT,
    producing_event_id TEXT NOT NULL, producing_seq INTEGER NOT NULL,
    branch_root TEXT NOT NULL, actor TEXT NOT NULL, created_at_ms INTEGER NOT NULL,
    content_hash TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS pointers (
    project_id TEXT PRIMARY KEY, branch_root TEXT NOT NULL,
    tip_event_id TEXT NOT NULL, tip_seq INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
