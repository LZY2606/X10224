package com.dsc.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 打开与迁移。user_version 记录结构版本：
 *   v1 初始结构；v2 增加 branch 表与 derived.stale_reason 列。
 */
public class Database {
    public static final int SCHEMA_VERSION = 2;

    private final Connection conn;

    private Database(Connection conn) {
        this.conn = conn;
    }

    public static Database open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL");
                s.execute("PRAGMA foreign_keys=ON");
                s.execute("PRAGMA busy_timeout=5000");
            }
            Database db = new Database(c);
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException("open db failed: " + path, e);
        }
    }

    public static Database inMemory() {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite::memory:");
            Database db = new Database(c);
            db.migrate();
            return db;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection conn() {
        return conn;
    }

    public void close() {
        try {
            conn.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int userVersion() throws SQLException {
        try (Statement s = conn.createStatement();
             var rs = s.executeQuery("PRAGMA user_version")) {
            return rs.getInt(1);
        }
    }

    private void migrate() throws SQLException {
        int v = userVersion();
        if (v == 0) {
            createAll();
            setUserVersion(SCHEMA_VERSION);
            return;
        }
        if (v < 2) {
            migrate1to2();
            setUserVersion(2);
        }
    }

    private void setUserVersion(int v) throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA user_version=" + v);
        }
    }

    private void migrate1to2() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("ALTER TABLE derived ADD COLUMN stale_reason TEXT NOT NULL DEFAULT '[]'");
            s.execute("""
                CREATE TABLE IF NOT EXISTS branch(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  base_version INTEGER NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  commit_json TEXT NOT NULL,
                  created_seq INTEGER NOT NULL)
                """);
        }
    }

    private void createAll() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.execute("""
                CREATE TABLE meta(
                  k TEXT PRIMARY KEY,
                  v TEXT NOT NULL)
                """);
            s.execute("INSERT INTO meta(k,v) VALUES('seq','0')");
            s.execute("""
                CREATE TABLE sample(
                  id TEXT PRIMARY KEY,
                  name TEXT NOT NULL,
                  created_seq INTEGER NOT NULL)
                """);
            s.execute("""
                CREATE TABLE source(
                  id TEXT NOT NULL,
                  sample_id TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  raw TEXT NOT NULL,
                  unit TEXT NOT NULL DEFAULT '',
                  meta TEXT NOT NULL DEFAULT '{}',
                  created_seq INTEGER NOT NULL,
                  PRIMARY KEY(id, version))
                """);
            s.execute("""
                CREATE TABLE window(
                  id TEXT NOT NULL,
                  sample_id TEXT NOT NULL,
                  segment_id TEXT NOT NULL,
                  t0 REAL NOT NULL,
                  t1 REAL NOT NULL,
                  label TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  created_seq INTEGER NOT NULL,
                  status TEXT NOT NULL DEFAULT 'active',
                  PRIMARY KEY(id, version))
                """);
            s.execute("""
                CREATE TABLE baseline(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  window_id TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  params TEXT NOT NULL,
                  status TEXT NOT NULL,
                  origin_event TEXT NOT NULL,
                  created_seq INTEGER NOT NULL)
                """);
            s.execute("""
                CREATE TABLE derived(
                  id TEXT NOT NULL,
                  sample_id TEXT NOT NULL,
                  kind TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  deps TEXT NOT NULL,
                  rule TEXT NOT NULL,
                  status TEXT NOT NULL,
                  payload TEXT NOT NULL,
                  diagnostics TEXT NOT NULL DEFAULT '[]',
                  stale_reason TEXT NOT NULL DEFAULT '[]',
                  created_seq INTEGER NOT NULL,
                  PRIMARY KEY(id, version))
                """);
            s.execute("""
                CREATE TABLE event(
                  id TEXT PRIMARY KEY,
                  seq INTEGER NOT NULL,
                  sample_id TEXT NOT NULL,
                  type TEXT NOT NULL,
                  payload TEXT NOT NULL,
                  actor TEXT NOT NULL)
                """);
            s.execute("CREATE INDEX idx_event_sample ON event(sample_id, seq)");
            s.execute("""
                CREATE TABLE head(
                  sample_id TEXT PRIMARY KEY,
                  version INTEGER NOT NULL,
                  seq INTEGER NOT NULL)
                """);
            s.execute("""
                CREATE TABLE head_history(
                  sample_id TEXT NOT NULL,
                  version INTEGER NOT NULL,
                  seq INTEGER NOT NULL)
                """);
            s.execute("""
                CREATE TABLE conflict(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  base_version INTEGER NOT NULL,
                  branch_a TEXT NOT NULL,
                  branch_b TEXT NOT NULL,
                  status TEXT NOT NULL,
                  resolution TEXT NOT NULL DEFAULT '',
                  created_seq INTEGER NOT NULL)
                """);
            s.execute("""
                CREATE TABLE branch(
                  id TEXT PRIMARY KEY,
                  sample_id TEXT NOT NULL,
                  base_version INTEGER NOT NULL,
                  name TEXT NOT NULL,
                  status TEXT NOT NULL,
                  commit_json TEXT NOT NULL,
                  created_seq INTEGER NOT NULL)
                """);
        }
    }
}
