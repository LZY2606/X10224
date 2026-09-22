package org.dsc.store;

/** SQLite schema。每个版本化实体保留全部版本，current_* 指针在同一事务内切换。 */
public final class Schema {
    private Schema() {}

    public static final int CURRENT_VERSION = 2;

    public static final String V1 = """
        CREATE TABLE IF NOT EXISTS meta(
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS events(
            event_id TEXT PRIMARY KEY,
            case_id TEXT NOT NULL,
            parent_id TEXT,
            seq INTEGER NOT NULL,
            type TEXT NOT NULL,
            actor TEXT NOT NULL,
            at TEXT NOT NULL,
            basis_version TEXT,
            payload TEXT NOT NULL,
            UNIQUE(case_id, seq)
        );
        CREATE TABLE IF NOT EXISTS sources(
            source_id TEXT NOT NULL,
            version INTEGER NOT NULL,
            case_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            segment_id TEXT,
            raw_text TEXT NOT NULL,
            unit TEXT,
            active INTEGER NOT NULL,
            superseded_by TEXT,
            created_event TEXT NOT NULL,
            PRIMARY KEY(source_id, version)
        );
        CREATE TABLE IF NOT EXISTS entities(
            entity_id TEXT NOT NULL,
            version INTEGER NOT NULL,
            case_id TEXT NOT NULL,
            kind TEXT NOT NULL,
            json TEXT NOT NULL,
            status TEXT NOT NULL,
            created_event TEXT NOT NULL,
            PRIMARY KEY(entity_id, version)
        );
        CREATE TABLE IF NOT EXISTS entity_pointers(
            case_id TEXT NOT NULL,
            entity_id TEXT NOT NULL,
            current_version INTEGER NOT NULL,
            updated_event TEXT NOT NULL,
            PRIMARY KEY(case_id, entity_id)
        );
        CREATE TABLE IF NOT EXISTS deps(
            from_entity TEXT NOT NULL,
            from_version INTEGER NOT NULL,
            to_entity TEXT NOT NULL,
            to_version INTEGER NOT NULL,
            event_id TEXT NOT NULL,
            PRIMARY KEY(from_entity, from_version, to_entity, to_version)
        );
        CREATE INDEX IF NOT EXISTS idx_deps_to ON deps(to_entity, to_version);
        CREATE INDEX IF NOT EXISTS idx_events_case ON events(case_id, seq);
        """;

    /** v2 迁移：实体标签（便于来源链展示）；老库补列。 */
    public static final String V2_ALTER =
        "ALTER TABLE entities ADD COLUMN label TEXT NOT NULL DEFAULT ''";

    public static final String V2_INDEX =
        "CREATE INDEX IF NOT EXISTS idx_entities_case ON entities(case_id, kind)";
}
