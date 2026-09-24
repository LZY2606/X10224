package dsc.store;

/**
 * SQLite schema definitions, versioned for migration tests.
 *
 * <p>Atomicity rule: a derivation is written to {@code derivations} and the current pointer in
 * {@code state_heads} inside one transaction. On restart after an interrupted write, readers see
 * either the old pointer or the complete new one, never a half-written state.
 */
public final class Schema {

    public static final int CURRENT_VERSION = 2;

    public static final String V1_DDL = """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS events (
              id TEXT PRIMARY KEY,
              seq INTEGER NOT NULL,
              type TEXT NOT NULL,
              actor TEXT NOT NULL,
              created_at TEXT NOT NULL,
              rule_version TEXT NOT NULL,
              basis_version INTEGER NOT NULL,
              payload TEXT NOT NULL,
              prev_hash TEXT NOT NULL,
              hash TEXT NOT NULL
            );
            CREATE UNIQUE INDEX IF NOT EXISTS idx_events_seq ON events(seq);
            CREATE TABLE IF NOT EXISTS state_heads (
              state_version INTEGER PRIMARY KEY,
              event_seq INTEGER NOT NULL,
              rule_version TEXT NOT NULL,
              created_at TEXT NOT NULL
            );
            """;

    /** v2 adds a frozen rules digest column used when restoring historical conclusions. */
    public static final String V2_DDL = """
            ALTER TABLE state_heads ADD COLUMN rules_digest TEXT NOT NULL DEFAULT '';
            """;

    public static final String V2_EXTRA = """
            CREATE TABLE IF NOT EXISTS derivations (
              state_version INTEGER NOT NULL,
              node_id TEXT NOT NULL,
              payload TEXT NOT NULL,
              status TEXT NOT NULL,
              PRIMARY KEY (state_version, node_id)
            );
            CREATE TABLE IF NOT EXISTS case_index (
              case_id TEXT PRIMARY KEY,
              title TEXT NOT NULL,
              created_at TEXT NOT NULL
            );
            """;

    private Schema() {
    }
}
