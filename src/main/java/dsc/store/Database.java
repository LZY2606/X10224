package dsc.store;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Low level SQLite connection management, schema migration and crash injection. */
public final class Database {

    public static final int CURRENT_SCHEMA = 3;

    private final String url;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Consumer<Connection> crashBeforeCommit;

    public Database(Path file) {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new IllegalStateException("cannot create data directory", e);
            }
        }
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        migrate();
    }

    private Database(String url) {
        this.url = url;
        migrate();
    }

    public static Database memory() {
        String name = "dscmem" + System.nanoTime();
        return new Database("jdbc:sqlite:file:" + name + "?mode=memory&cache=shared");
    }

    public Connection connect() {
        try {
            Connection c = DriverManager.getConnection(url);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
                st.execute("PRAGMA busy_timeout = 5000");
            }
            return c;
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open database: " + url, e);
        }
    }

    /**
     * Run a unit of work inside a single transaction. The current pointer only switches
     * after every row of the new version is written, so a crash before commit leaves
     * the previous complete version visible.
     */
    public <T> T transactionReturn(java.util.function.Function<Connection, T> work) {
        @SuppressWarnings("unchecked")
        java.util.concurrent.atomic.AtomicReference<T> ref = new java.util.concurrent.atomic.AtomicReference<>();
        transaction(c -> ref.set(work.apply(c)));
        return ref.get();
    }

    public void transaction(Consumer<Connection> work) {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                work.accept(c);
                Consumer<Connection> hook = crashBeforeCommit;
                if (hook != null) {
                    hook.accept(c);
                }
                c.commit();
            } catch (RuntimeException e) {
                rollback(c);
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Test hook: simulate process loss immediately before commit, without committing. */
    public void crashOnNextCommit() {
        crashBeforeCommit = c -> {
            throw new SimulatedCrash("simulated crash before commit");
        };
    }

    public void clearCrashHook() {
        crashBeforeCommit = null;
    }

    private static void rollback(Connection c) {
        try {
            c.rollback();
        } catch (SQLException ignored) {
            // connection is discarded regardless
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    private void migrate() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            Integer version = readVersion(c);
            if (version == null) {
                applyScript(c, readResource("/schema-v3.sql"));
                putVersion(c, CURRENT_SCHEMA);
            } else if (version == 1) {
                applyScript(c, readResource("/schema-migration-v1-v2.sql"));
                applyScript(c, readResource("/schema-migration-v2-v3.sql"));
                putVersion(c, CURRENT_SCHEMA);
            } else if (version == 2) {
                applyScript(c, readResource("/schema-migration-v2-v3.sql"));
                putVersion(c, CURRENT_SCHEMA);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("migration failed", e);
        }
    }

    private static Integer readVersion(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT value FROM meta WHERE key='schemaVersion'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return Integer.parseInt(rs.getString(1));
        }
    }

    private static void putVersion(Connection c, int version) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO meta(key,value) VALUES('schemaVersion',?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, Integer.toString(version));
            ps.executeUpdate();
        }
    }

    private static String readResource(String resource) {
        try (InputStream in = Database.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("missing classpath resource " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void applyScript(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            StringBuilder current = new StringBuilder();
            boolean lineComment = false;
            for (String rawLine : sql.split("\\r?\\n", -1)) {
                String trimmed = rawLine.strip();
                if (trimmed.startsWith("--")) {
                    continue;
                }
                current.append(rawLine).append('\n');
                if (trimmed.endsWith(";")) {
                    String stmt = current.toString().strip();
                    if (!stmt.isEmpty()) {
                        st.execute(stmt);
                    }
                    current.setLength(0);
                }
            }
            if (!current.toString().strip().isEmpty()) {
                st.execute(current.toString().strip());
            }
        }
    }

    public Map<String, String> meta() {
        Map<String, String> out = new LinkedHashMap<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT key, value FROM meta");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return out;
    }
}
