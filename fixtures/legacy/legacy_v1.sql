-- legacy schema v1: events table without deps column, objects without created_by_event
CREATE TABLE schema_migrations(version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL);
INSERT INTO schema_migrations(version, applied_at) VALUES (1, '2026-01-01T00:00:00Z');
CREATE TABLE objects(
  id TEXT NOT NULL,
  kind TEXT NOT NULL,
  version INTEGER NOT NULL,
  json TEXT NOT NULL,
  PRIMARY KEY(id, version)
);
CREATE TABLE current(
  key TEXT PRIMARY KEY,
  object_id TEXT NOT NULL,
  version INTEGER NOT NULL
);
CREATE TABLE events(
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  type TEXT NOT NULL,
  payload TEXT NOT NULL,
  ts TEXT NOT NULL
);
INSERT INTO objects(id, kind, version, json) VALUES
  ('doc-mass-1','source',1,'{"kind":"SAMPLE_MASS","raw":"mass: 12.46 mg"}');
INSERT INTO current(key, object_id, version) VALUES ('mass:PET-flake-A','doc-mass-1',1);
INSERT INTO events(id, type, payload, ts) VALUES
  ('ev-legacy-1','IMPORT_SOURCE','{"doc":"doc-mass-1"}','2026-01-01T00:00:01Z');
