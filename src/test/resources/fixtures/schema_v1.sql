-- Legacy v1 database as shipped by the first review build.
PRAGMA user_version = 1;
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE events (
    seq INTEGER PRIMARY KEY,
    id TEXT NOT NULL UNIQUE,
    case_id TEXT NOT NULL,
    type TEXT NOT NULL,
    actor TEXT NOT NULL,
    expected_version TEXT NOT NULL,
    ts TEXT NOT NULL,
    payload TEXT NOT NULL
);
CREATE TABLE docs (
    id TEXT NOT NULL,
    version TEXT NOT NULL,
    lineage TEXT NOT NULL,
    kind TEXT NOT NULL,
    seq INTEGER NOT NULL,
    base_version TEXT NOT NULL,
    data TEXT NOT NULL,
    PRIMARY KEY (id, version)
);
CREATE TABLE pointers (
    lineage TEXT NOT NULL,
    seq INTEGER NOT NULL,
    doc_id TEXT NOT NULL,
    version TEXT NOT NULL,
    PRIMARY KEY (lineage, seq)
);
CREATE TABLE dep (
    derived_id TEXT NOT NULL,
    derived_version TEXT NOT NULL,
    source_id TEXT NOT NULL,
    source_version TEXT NOT NULL,
    role TEXT NOT NULL,
    PRIMARY KEY (derived_id, derived_version, source_id, source_version, role)
);
