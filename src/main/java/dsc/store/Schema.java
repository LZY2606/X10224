package dsc.store;

/**
 * SQLite schema. All archived rows are immutable and versioned; pointer/state tables hold
 * only the current view. A write transaction covers event + archive + pointer updates
 * together, so a crash leaves either the old or the fully-new version, never a torn one.
 */
public final class Schema {

    public static final int CURRENT_VERSION = 2;

    public static final String V2 = """
            PRAGMA journal_mode=WAL;
            PRAGMA foreign_keys=ON;

            CREATE TABLE IF NOT EXISTS schema_meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS project (
              id TEXT PRIMARY KEY,
              body TEXT NOT NULL,
              version INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS segments (
              id TEXT PRIMARY KEY,
              body TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sources (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              kind TEXT NOT NULL,
              segment_id TEXT,
              supersedes TEXT,
              body TEXT NOT NULL,
              PRIMARY KEY (id, version)
            );

            CREATE TABLE IF NOT EXISTS intervals (
              id TEXT PRIMARY KEY,
              body TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS tangents (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              body TEXT NOT NULL,
              PRIMARY KEY (id, version)
            );

            CREATE TABLE IF NOT EXISTS baselines (
              id TEXT PRIMARY KEY,
              interval_id TEXT NOT NULL,
              accepted INTEGER NOT NULL DEFAULT 0,
              body TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS results (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              interval_id TEXT NOT NULL,
              current INTEGER NOT NULL DEFAULT 0,
              body TEXT NOT NULL,
              PRIMARY KEY (id, version)
            );

            CREATE TABLE IF NOT EXISTS derivations (
              id TEXT NOT NULL,
              version INTEGER NOT NULL,
              body TEXT NOT NULL,
              PRIMARY KEY (id, version)
            );

            CREATE TABLE IF NOT EXISTS decisions (
              id TEXT PRIMARY KEY,
              body TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS anomalies (
              id TEXT PRIMARY KEY,
              body TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS pointers (
              pkey TEXT PRIMARY KEY,
              pvalue TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS events (
              seq INTEGER PRIMARY KEY AUTOINCREMENT,
              id TEXT NOT NULL UNIQUE,
              body TEXT NOT NULL,
              created_epoch INTEGER NOT NULL
            );

            CREATE INDEX IF NOT EXISTS idx_sources_lookup ON sources(kind, segment_id);
            CREATE INDEX IF NOT EXISTS idx_baselines_interval ON baselines(interval_id);
            CREATE INDEX IF NOT EXISTS idx_results_interval ON results(interval_id, current);
            """;

    private Schema() {
    }
}
