-- Thermal Turning Point case database, schema v2.
PRAGMA user_version = 2;

CREATE TABLE IF NOT EXISTS meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS events (
    seq              INTEGER PRIMARY KEY,
    id               TEXT NOT NULL UNIQUE,
    case_id          TEXT NOT NULL,
    type             TEXT NOT NULL,
    actor            TEXT NOT NULL,
    expected_version TEXT NOT NULL,
    ts               TEXT NOT NULL,
    payload          TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_events_case ON events(case_id, seq);

-- Every derived/source revision. (id, version) is immutable; revisions share id+lineage.
CREATE TABLE IF NOT EXISTS docs (
    id           TEXT NOT NULL,
    version      TEXT NOT NULL,
    lineage      TEXT NOT NULL,
    kind         TEXT NOT NULL,
    seq          INTEGER NOT NULL,
    base_version TEXT NOT NULL,
    data         TEXT NOT NULL,
    PRIMARY KEY (id, version)
);
CREATE INDEX IF NOT EXISTS idx_docs_lineage ON docs(lineage, seq);
CREATE INDEX IF NOT EXISTS idx_docs_kind ON docs(kind);

-- Current pointer per lineage as of event seq (explicit history enables branch switches).
CREATE TABLE IF NOT EXISTS pointers (
    lineage TEXT NOT NULL,
    seq     INTEGER NOT NULL,
    doc_id  TEXT NOT NULL,
    version TEXT NOT NULL,
    PRIMARY KEY (lineage, seq)
);

-- Dependency edges captured at derivation time: derived doc version -> source doc version.
CREATE TABLE IF NOT EXISTS dep (
    derived_id      TEXT NOT NULL,
    derived_version TEXT NOT NULL,
    source_id       TEXT NOT NULL,
    source_version  TEXT NOT NULL,
    role            TEXT NOT NULL,
    PRIMARY KEY (derived_id, derived_version, source_id, source_version, role)
);
CREATE INDEX IF NOT EXISTS idx_dep_source ON dep(source_id, source_version);

-- v2 addition: reusable saved conflict resolutions (introduced by migration v1 -> v2).
CREATE TABLE IF NOT EXISTS conflict_resolutions (
    conflict_event_id TEXT PRIMARY KEY,
    case_id           TEXT NOT NULL,
    resolution        TEXT NOT NULL,
    seq               INTEGER NOT NULL
);
