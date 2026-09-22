package dsc.store;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class Db {

    private Db() {
    }

    public static Connection open(Path file) {
        try {
            String url;
            if (file == null || file.toString().isBlank()) {
                url = "jdbc:sqlite::memory:";
            } else {
                url = "jdbc:sqlite:" + file.toAbsolutePath();
            }
            Connection c = DriverManager.getConnection(url);
            configure(c);
            migrate(c);
            return c;
        } catch (SQLException e) {
            throw new RuntimeException("cannot open database: " + file, e);
        }
    }

    private static void configure(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=10000");
            st.execute("PRAGMA synchronous=FULL");
            c.commit();
        }
    }

    static int userVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
             var rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    static void migrate(Connection c) throws SQLException {
        int version = userVersion(c);
        if (version == 0) {
            try (Statement st = c.createStatement()) {
                for (String ddl : Schema.createV1().split(";")) {
                    if (!ddl.isBlank()) {
                        st.execute(ddl);
                    }
                }
                st.execute("PRAGMA user_version=1");
                st.execute("PRAGMA user_version=2");
            }
            seedTrunk(c);
            c.commit();
            return;
        }
        if (version == 1) {
            try (Statement st = c.createStatement()) {
                for (String ddl : Schema.migrateV1ToV2().split(";")) {
                    if (!ddl.isBlank()) {
                        st.execute(ddl);
                    }
                }
                st.execute("PRAGMA user_version=2");
            }
            c.commit();
        }
    }

    private static void seedTrunk(Connection c) throws SQLException {
        try (var ps = c.prepareStatement(
                "INSERT INTO branches(name, parent_branch, forked_from_event_id, "
                        + "head_event_id, created_at_millis) VALUES(?,?,?,?,?)")) {
            ps.setString(1, "trunk");
            ps.setObject(2, null);
            ps.setObject(3, null);
            ps.setObject(4, null);
            ps.setLong(5, 0L);
            ps.executeUpdate();
        }
    }
}
