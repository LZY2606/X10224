-- v2 -> v3: lineage sources gain their producing event, plus intervals and replay marks.
ALTER TABLE sources ADD COLUMN producing_event_id TEXT NOT NULL DEFAULT '';
-- v2 -> v3: draggable intervals and replay marks join the append-only model.
CREATE TABLE IF NOT EXISTS intervals (
    interval_id TEXT PRIMARY KEY,
    project_id  TEXT NOT NULL,
    segment_id  TEXT NOT NULL,
    label       TEXT NOT NULL,
    start_c     REAL NOT NULL,
    end_c       REAL NOT NULL,
    created_seq INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS replay_marks (
    project_id TEXT NOT NULL,
    case_key TEXT NOT NULL,
    mark TEXT NOT NULL,
    at_seq INTEGER NOT NULL,
    PRIMARY KEY (project_id, case_key, mark)
);

CREATE INDEX IF NOT EXISTS idx_sources_project ON sources(project_id, kind);
CREATE INDEX IF NOT EXISTS idx_events_project ON events(project_id, seq);
CREATE INDEX IF NOT EXISTS idx_deriv_project ON derivations(project_id, kind, interval_id);
CREATE INDEX IF NOT EXISTS idx_deriv_event ON derivations(producing_event_id);
