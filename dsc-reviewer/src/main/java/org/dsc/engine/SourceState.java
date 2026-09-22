package org.dsc.engine;

import org.dsc.store.Store;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** 读取某稳定来源当前版本号（不存在时为 0）。 */
final class SourceState {
    private SourceState() {}

    static int versionOf(Store store, String sourceId) {
        try (PreparedStatement ps = store.connection().prepareStatement(
                "SELECT COALESCE(MAX(version),0) FROM sources WHERE source_id=?")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (Exception e) {
            throw new Store.StoreException("source version lookup failed", e);
        }
    }

    static String activeRaw(Store store, String sourceId) {
        try (PreparedStatement ps = store.connection().prepareStatement(
                "SELECT raw_text FROM sources WHERE source_id=? AND active=1 ORDER BY version DESC LIMIT 1")) {
            ps.setString(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            throw new Store.StoreException("source raw lookup failed", e);
        }
    }
}
