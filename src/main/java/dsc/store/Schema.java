package dsc.store;

/** SQLite schema. Event log is the source of truth; derived tables cache versions. */
public final class Schema {

    public static final int CURRENT_VERSION = 2;

    public static final String DDL_V1 = """
            CREATE TABLE IF NOT EXISTS meta (
              key TEXT PRIMARY KEY,
              value TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS events (
              seq INTEGER PRIMARY KEY AUTOINCREMENT,
              id TEXT NOT NULL UNIQUE,
              type TEXT NOT NULL,
              actor TEXT NOT NULL,
              ts TEXT NOT NULL,
              branch TEXT NOT NULL,
              parent_event_id TEXT,
              payload_json TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS raw_segments (
              event_id TEXT NOT NULL,
              segment_id TEXT NOT NULL,
              label TEXT,
              source_file TEXT,
              cycle_index INTEGER NOT NULL,
              declared_direction TEXT,
              exo_convention TEXT,
              PRIMARY KEY (event_id, segment_id)
            );

            CREATE TABLE IF NOT EXISTS raw_points (
              event_id TEXT NOT NULL,
              segment_id TEXT NOT NULL,
              idx INTEGER NOT NULL,
              time_val REAL, time_unit TEXT,
              temp_val REAL, temp_unit TEXT,
              flow_val REAL, flow_unit TEXT,
              valid INTEGER NOT NULL,
              diagnostic TEXT,
              PRIMARY KEY (event_id, segment_id, idx)
            );

            CREATE TABLE IF NOT EXISTS pointers (
              name TEXT PRIMARY KEY,
              event_id TEXT NOT NULL,
              seq INTEGER NOT NULL,
              version INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS branches (
              name TEXT PRIMARY KEY,
              head_seq INTEGER NOT NULL,
              parent_branch TEXT,
              forked_at_seq INTEGER
            );

            CREATE TABLE IF NOT EXISTS conflicts (
              id TEXT PRIMARY KEY,
              created_seq INTEGER NOT NULL,
              status TEXT NOT NULL,
              base_version INTEGER NOT NULL,
              payload_json TEXT NOT NULL,
              resolution_json TEXT
            );
            """;

    private Schema() {
    }
}
