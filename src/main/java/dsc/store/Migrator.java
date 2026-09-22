package dsc.store;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite user_version migrations. The repository ships a hand-written v1 fixture so the
 * "old version migration" requirement is exercised against a real legacy database rather
 * than a mocked one.
 */
public final class Migrator {

    public static final int CURRENT_VERSION = 2;

    private Migrator() {}

    static void migrate(Connection cx) throws SQLException {
        int v = userVersion(cx);
        if (v == 0) {
            // Fresh database: create current schema directly.
            applyScript(cx, readResource("/db/schema.sql"));
            return;
        }
        if (v == 1) {
            migrateV1ToV2(cx);
            return;
        }
        if (v > CURRENT_VERSION) {
            throw new SQLException("database schema version " + v + " is newer than this build (" + CURRENT_VERSION + ")");
        }
    }

    static void migrateV1ToV2(Connection cx) throws SQLException {
        // v1 lacked the conflict_resolutions table and user-settable resolutions.
        try (Statement st = cx.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS conflict_resolutions ("
                    + "conflict_event_id TEXT PRIMARY KEY, "
                    + "case_id TEXT NOT NULL, "
                    + "resolution TEXT NOT NULL, "
                    + "seq INTEGER NOT NULL)");
            st.execute("PRAGMA user_version = 2");
        }
        cx.commit();
    }

    private static int userVersion(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static void applyScript(Connection cx, String script) throws SQLException {
        try (Statement st = cx.createStatement()) {
            for (String stmt : script.split(";")) {
                String trimmed = stripComments(stmt).trim();
                if (!trimmed.isEmpty()) st.execute(trimmed);
            }
        }
        cx.commit();
    }

    private static String stripComments(String s) {
        StringBuilder out = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.trim();
            if (t.startsWith("--")) continue;
            out.append(line).append('\n');
        }
        return out.toString();
    }

    private static String readResource(String name) {
        try (InputStream in = Migrator.class.getResourceAsStream(name)) {
            if (in == null) throw new SQLException("missing resource " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new SQLException("cannot read " + name, e);
        }
    }
}
