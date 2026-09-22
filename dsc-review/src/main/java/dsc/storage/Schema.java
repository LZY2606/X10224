package dsc.storage;

/**
 * SQLite DDL. Schema versions are numbered; {@link Migrator} upgrades old
 * databases in place so a database written by an older application keeps
 * replaying instead of being rebuilt.
 */
final class Schema {

    static final int CURRENT_VERSION = 2;

    /** v1: the first released shape (a history row + append-only events). */
    static final String V1_EVENTS = """
            CREATE TABLE IF NOT EXISTS events (
                seq           INTEGER PRIMARY KEY AUTOINCREMENT,
                history_id    TEXT NOT NULL,
                entity_id     TEXT,
                type          TEXT NOT NULL,
                schema_ver    INTEGER NOT NULL DEFAULT 1,
                created_at    TEXT NOT NULL,
                actor         TEXT,
                comment       TEXT,
                payload_json  TEXT NOT NULL,
                judgments_json TEXT,
                base_seq      INTEGER,
                deps_json     TEXT,
                supersedes    TEXT
            );
            CREATE INDEX IF NOT EXISTS idx_events_history ON events(history_id, seq);
            CREATE INDEX IF NOT EXISTS idx_events_entity  ON events(entity_id);
            """;

    static final String V1_HISTORIES = """
            CREATE TABLE IF NOT EXISTS histories (
                history_id  TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                opened_at   TEXT NOT NULL
            );
            """;

    /** v2 adds snapshot/pointer and export bookkeeping without changing v1 rows. */
    static final String V2_POINTERS = """
            CREATE TABLE IF NOT EXISTS current_pointers (
                history_id  TEXT NOT NULL,
                slot        TEXT NOT NULL,
                entity_id   TEXT NOT NULL,
                event_seq   INTEGER NOT NULL,
                PRIMARY KEY (history_id, slot)
            );
            CREATE TABLE IF NOT EXISTS export_log (
                history_id  TEXT NOT NULL,
                export_id   TEXT NOT NULL,
                exported_at TEXT NOT NULL,
                PRIMARY KEY (history_id, export_id)
            );
            """;

    static final String META = """
            CREATE TABLE IF NOT EXISTS meta (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """;

    private Schema() {}
}
