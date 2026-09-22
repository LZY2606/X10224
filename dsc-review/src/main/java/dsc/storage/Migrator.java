package dsc.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Forward-only schema migration. Older databases are upgraded, never wiped. */
public final class Migrator {

    private Migrator() {}

    public static int currentVersion() { return Schema.CURRENT_VERSION; }

    public static void migrate(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(Schema.META);
        }
        int version = readVersion(cx);
        if (version < 1) {
            try (Statement st = cx.createStatement()) {
                st.execute(Schema.V1_HISTORIES);
                st.execute(Schema.V1_EVENTS);
            }
            version = 1;
            writeVersion(cx, version);
        }
        if (version < 2) {
            try (Statement st = cx.createStatement()) {
                st.execute(Schema.V2_POINTERS);
            }
            version = 2;
            writeVersion(cx, version);
        }
        if (version > Schema.CURRENT_VERSION) {
            throw new SQLException("database schema version " + version
                    + " is newer than application " + Schema.CURRENT_VERSION);
        }
    }

    /** Test/legacy helper: stamp an empty database at a specific old version with v1 tables. */
    public static void stampLegacyV1(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement()) {
            st.execute(Schema.META);
            st.execute(Schema.V1_HISTORIES);
            st.execute(Schema.V1_EVENTS);
        }
        writeVersion(cx, 1);
    }

    private static int readVersion(Connection cx) throws SQLException {
        try (var rs = cx.createStatement().executeQuery("SELECT value FROM meta WHERE key='schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString(1)) : 0;
        }
    }

    private static void writeVersion(Connection cx, int v) throws SQLException {
        try (var ps = cx.prepareStatement(
                "INSERT INTO meta(key, value) VALUES('schema_version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value=excluded.value")) {
            ps.setString(1, Integer.toString(v));
            ps.executeUpdate();
        }
    }
}
