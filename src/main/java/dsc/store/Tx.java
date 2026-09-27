package dsc.store;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * A unit of atomic work plus an explicit crash marker.
 *
 * Contract: rows written between {@link #begin()} and {@link #commit()} are
 * only visible after commit. If the process dies, {@code changeset} keeps an
 * OPEN marker; on next open Database discards the marker and the database
 * contains either the previous committed state or (had commit finished) the
 * new state, because SQLite itself guarantees atomicity of the commit.
 */
public final class Tx implements AutoCloseable {
    private final Database db;
    private final String changesetId;
    private boolean finished;

    private Tx(Database db) {
        this.db = db;
        this.changesetId = "cs_" + UUID.randomUUID();
    }

    public static Tx begin(Database db) {
        Tx tx = new Tx(db);
        try {
            Connection c = db.conn();
            c.setAutoCommit(false);
            try (var ps = c.prepareStatement(
                    "INSERT INTO changeset(id,started_at,status) VALUES(?,?, 'OPEN')")) {
                ps.setString(1, tx.changesetId);
                ps.setString(2, Instant.now().toString());
                ps.executeUpdate();
            }
            return tx;
        } catch (SQLException e) {
            throw new Database.StoreException("begin failed", e);
        }
    }

    public String changesetId() { return changesetId; }

    public void commit() {
        try {
            Connection c = db.conn();
            try (var ps = c.prepareStatement(
                    "UPDATE changeset SET status='COMMITTED' WHERE id=?")) {
                ps.setString(1, changesetId);
                ps.executeUpdate();
            }
            c.commit();
            finished = true;
        } catch (SQLException e) {
            throw new Database.StoreException("commit failed", e);
        }
    }

    @Override
    public void close() {
        try {
            Connection c = db.conn();
            if (!finished) c.rollback();
            c.setAutoCommit(true);
            if (!finished) {
                try (var ps = c.prepareStatement("DELETE FROM changeset WHERE id=?")) {
                    ps.setString(1, changesetId);
                    ps.executeUpdate();
                }
            } else {
                try (var ps = c.prepareStatement("DELETE FROM changeset WHERE id=?")) {
                    ps.setString(1, changesetId);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new Database.StoreException("close tx failed", e);
        }
    }
}
