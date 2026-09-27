-- v1 -> v2: lineage branches and current pointers.
CREATE TABLE IF NOT EXISTS branches (
    branch_root TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    label TEXT NOT NULL,
    tip_event_id TEXT NOT NULL,
    tip_seq INTEGER NOT NULL,
    status TEXT NOT NULL,
    forked_from_event_id TEXT
);

CREATE TABLE IF NOT EXISTS pointers (
    project_id TEXT PRIMARY KEY,
    branch_root TEXT NOT NULL,
    tip_event_id TEXT NOT NULL,
    tip_seq INTEGER NOT NULL
);
