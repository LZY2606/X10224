-- Legacy v1 schema used by migration tests.
CREATE TABLE IF NOT EXISTS projects (
    project_id TEXT PRIMARY KEY, code TEXT NOT NULL, name TEXT NOT NULL, created_at_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS sources (
    source_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, kind TEXT NOT NULL,
    filename TEXT NOT NULL, raw_payload TEXT NOT NULL, raw_fields_json TEXT NOT NULL DEFAULT '{}',
    revises_source_id TEXT, ingest_seq INTEGER NOT NULL,
    branch_root TEXT NOT NULL DEFAULT 'main', actor TEXT NOT NULL DEFAULT 'legacy', created_at_ms INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS events (
    seq INTEGER PRIMARY KEY AUTOINCREMENT, event_id TEXT NOT NULL UNIQUE,
    project_id TEXT NOT NULL, parent_event_id TEXT, branch_root TEXT NOT NULL DEFAULT 'main',
    type TEXT NOT NULL, actor TEXT NOT NULL DEFAULT 'legacy', created_at_ms INTEGER NOT NULL,
    expected_base_seq INTEGER NOT NULL DEFAULT 0, payload_json TEXT NOT NULL DEFAULT '{}'
);
CREATE TABLE IF NOT EXISTS derivations (
    derivation_id TEXT PRIMARY KEY, project_id TEXT NOT NULL, kind TEXT NOT NULL,
    interval_id TEXT, status TEXT NOT NULL DEFAULT 'OK', rule_version TEXT NOT NULL,
    inputs_json TEXT NOT NULL DEFAULT '{}', payload_json TEXT NOT NULL DEFAULT '{}',
    error_code TEXT, producing_event_id TEXT NOT NULL, producing_seq INTEGER NOT NULL,
    branch_root TEXT NOT NULL DEFAULT 'main', actor TEXT NOT NULL DEFAULT 'legacy',
    created_at_ms INTEGER NOT NULL, content_hash TEXT NOT NULL DEFAULT 'legacy'
);
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
