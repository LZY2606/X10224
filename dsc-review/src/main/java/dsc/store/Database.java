package dsc.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite storage. The event log is append-only; every derivation write is a single
 * transaction that ends by flipping the current pointer, so a crash mid-write leaves
 * either the old complete version or the new complete version.
 */
public final class Database implements AutoCloseable {
    public final Connection cx;
    public final boolean wal;

    public Database(String path) {
        this(path, true);
    }

    public Database(String path, boolean wal) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.cx = DriverManager.getConnection("jdbc:sqlite:" + path);
            this.wal = wal;
            if (wal) {
                try (Statement st = cx.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA busy_timeout=5000");
                    st.execute("PRAGMA foreign_keys=ON");
                }
            } else {
                try (Statement st = cx.createStatement()) {
                    st.execute("PRAGMA foreign_keys=ON");
                }
            }
            migrate();
        } catch (Exception e) { throw new IllegalStateException("open db " + path, e); }
    }

    /** Versioned schema. Old files migrate forward without losing event history. */
    public void migrate() throws SQLException {
        int v = userVersion();
        if (v < 1) {
            exec("""
                create table if not exists meta(
                  key text primary key,
                  value text not null
                )""");
            exec("""
                create table if not exists sources(
                  id text not null,
                  version integer not null,
                  kind text not null,
                  content_json text not null,
                  supersedes text,
                  created_event_seq integer not null,
                  primary key (id, version)
                )""");
            exec("""
                create table if not exists events(
                  seq integer primary key,
                  id text not null unique,
                  branch text not null,
                  type text not null,
                  actor text not null,
                  at text not null,
                  payload_json text not null,
                  basis_seq text,
                  parent_event_id text
                )""");
            exec("""
                create table if not exists derived(
                  id text primary key,
                  kind text not null,
                  segment_id text,
                  content_json text not null,
                  input_hash text not null,
                  rule_version text not null,
                  dep_hashes_json text not null,
                  created_event_seq integer not null,
                  superseded_by text
                )""");
            // current pointer per (kind, scope); atomic flip inside the writing transaction
            exec("""
                create table if not exists current_ptr(
                  scope text not null,
                  kind text not null,
                  derived_id text not null,
                  updated_event_seq integer not null,
                  primary key (scope, kind)
                )""");
            exec("""
                create table if not exists intervals(
                  id text not null,
                  version integer not null,
                  segment_id text not null,
                  content_json text not null,
                  created_event_seq integer not null,
                  primary key (id, version)
                )""");
            setUserVersion(1);
            v = 1;
        }
        if (v < 2) {
            // v2: remember replay/history pointer per branch so time-travel is explicit
            exec("alter table current_ptr add column history_seq integer");
            setUserVersion(2);
        }
    }

    public int userVersion() throws SQLException {
        try (var rs = cx.createStatement().executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public int userVersionSafe() {
        try { return userVersion(); } catch (SQLException e) { return 0; }
    }

    private void setUserVersion(int v) throws SQLException {
        cx.createStatement().execute("PRAGMA user_version=" + v);
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = cx.createStatement()) { st.execute(sql); }
    }

    @Override public void close() { try { cx.close(); } catch (SQLException ignored) {} }
}
