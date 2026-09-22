package dsc.store;

/**
 * Storage schema history. Migration from older versions is exercised by the
 * automated tests; current version is {@link #CURRENT}.
 */
final class Schema {

    static final int CURRENT = 2;

    private Schema() {
    }

    static String createV1() {
        return """
                CREATE TABLE events (
                  id TEXT PRIMARY KEY,
                  parent_id TEXT,
                  branch TEXT NOT NULL,
                  seq INTEGER NOT NULL,
                  actor TEXT NOT NULL,
                  type TEXT NOT NULL,
                  created_at_millis INTEGER NOT NULL,
                  client_key TEXT,
                  expects_parent_id TEXT,
                  payload_json TEXT NOT NULL,
                  UNIQUE(branch, client_key)
                );
                CREATE INDEX events_branch_seq ON events(branch, seq);

                CREATE TABLE branches (
                  name TEXT PRIMARY KEY,
                  parent_branch TEXT,
                  forked_from_event_id TEXT,
                  head_event_id TEXT,
                  created_at_millis INTEGER NOT NULL
                );

                CREATE TABLE derived_versions (
                  derived_id TEXT NOT NULL,
                  branch TEXT NOT NULL,
                  node_id TEXT NOT NULL,
                  inputs_hash TEXT NOT NULL,
                  rules_version TEXT NOT NULL,
                  status TEXT NOT NULL,
                  complete INTEGER NOT NULL,
                  json TEXT,
                  created_at_millis INTEGER NOT NULL,
                  PRIMARY KEY (derived_id, branch)
                );
                CREATE INDEX derived_cursor ON derived_versions(branch, node_id, created_at_millis);

                CREATE TABLE kv (k TEXT PRIMARY KEY, v TEXT NOT NULL);
                """;
    }

    /**
     * V2 adds provenance columns to derived_versions and an audit row per
     * successful append, so the decision behind every pointer switch is
     * recoverable from the database alone.
     */
    static String migrateV1ToV2() {
        return """
                ALTER TABLE derived_versions ADD COLUMN caused_by_event_id TEXT;
                ALTER TABLE derived_versions ADD COLUMN adopted_id TEXT;
                CREATE TABLE append_audit (
                  event_id TEXT PRIMARY KEY,
                  branch TEXT NOT NULL,
                  previous_head TEXT,
                  new_head TEXT NOT NULL,
                  at_millis INTEGER NOT NULL
                );
                """;
    }
}
