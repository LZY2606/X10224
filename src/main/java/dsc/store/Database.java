package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    public static final ObjectMapper JSON = new ObjectMapper();
    public static final int SCHEMA_VERSION = 2;

    private final String path;

    public Database(String path) {
        this.path = path;
    }

    public Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA foreign_keys=ON");
        }
        return c;
    }

    /** Create the current (v2) schema if absent, or migrate an older one. */
    public void init() {
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            Integer v = null;
            var rs = s.executeQuery("SELECT version FROM schema_version");
            if (rs.next()) v = rs.getInt(1);
            if (v == null) {
                createV2(c);
                s.execute("INSERT INTO schema_version(version) VALUES (" + SCHEMA_VERSION + ")");
            } else if (v < SCHEMA_VERSION) {
                Migrator.migrate(c, v, SCHEMA_VERSION);
                s.execute("UPDATE schema_version SET version=" + SCHEMA_VERSION);
            }
        } catch (SQLException e) {
            throw new RuntimeException("init failed: " + e.getMessage(), e);
        }
    }

    static void createV2(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS sources(" +
                    "id TEXT PRIMARY KEY, kind TEXT NOT NULL, version INTEGER NOT NULL," +
                    "payload TEXT NOT NULL, seq INTEGER NOT NULL, replaces TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS derived(" +
                    "id TEXT PRIMARY KEY, kind TEXT NOT NULL, version INTEGER NOT NULL," +
                    "inputs TEXT NOT NULL, status TEXT NOT NULL, payload TEXT NOT NULL, seq INTEGER NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS events(" +
                    "seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL," +
                    "type TEXT NOT NULL, payload TEXT NOT NULL, created_at TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS pointers(name TEXT PRIMARY KEY, version INTEGER NOT NULL)");
            // v2 addition: optimistic-concurrency conflicts between two editors
            s.execute("CREATE TABLE IF NOT EXISTS conflicts(" +
                    "id TEXT PRIMARY KEY, base_version INTEGER NOT NULL, current_version INTEGER NOT NULL," +
                    "payload TEXT NOT NULL, status TEXT NOT NULL, seq INTEGER NOT NULL)");
        }
    }

    public static ObjectNode obj() {
        return JSON.createObjectNode();
    }

    public static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String write(JsonNode n) {
        try {
            return JSON.writeValueAsString(n);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
