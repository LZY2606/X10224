package dsc.store;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.core.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * 领域服务：所有写操作以事件落库；派生结果独立版本化，仅在完整成功后切换当前指针；
 * 基于依赖图传播的重算只更新受影响部分；并发编辑产生可解决冲突。
 */
public final class DscService {

    private final Db db;
    private final EventStore events;
    private Runnable crashHook = () -> {};
    private List<Map<String, Object>> lastRecomputeReport = List.of();

    public DscService(Db db) {
        this.db = db;
        this.events = new EventStore(db);
    }

    /** 测试钩子：在派生行写入（building）与当前指针切换之间触发，用于模拟写入中断。 */
    public void setCrashHook(Runnable hook) { this.crashHook = hook; }

    // ---------- 查询 ----------

    public State state() { return Projector.project(events.readAll()); }

    public State stateAt(long seq) { return Projector.project(events.readRange(0, seq)); }

    public long currentSeq() { return db.currentSeq(); }

    public List<EventStore.Event> eventsAfter(long seq) { return events.readRange(seq, Long.MAX_VALUE); }

    /** 读回派生结果（任何旧版本都可按稳定 id 读回）。 */
    public JsonNode derivedResult(String derivedId) {
        JsonNode row = derivedRow(derivedId);
        return row == null ? null : row.get("result");
    }

    public JsonNode derivedRow(String derivedId) {
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id,kind,subject,version,status,deps,result FROM derived WHERE id=?")) {
            ps.setString(1, derivedId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                ObjectNode n = EventStore.obj();
                n.put("id", rs.getString(1));
                n.put("kind", rs.getString(2));
                n.put("subject", rs.getString(3));
                n.put("version", rs.getLong(4));
                n.put("status", rs.getString(5));
                n.set("deps", EventStore.MAPPER.readTree(rs.getString(6)));
                if (rs.getString(7) != null) n.set("result", EventStore.MAPPER.readTree(rs.getString(7)));
                return n;
            }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public List<JsonNode> derivedHistory(String subject) {
        List<JsonNode> out = new ArrayList<>();
        try (PreparedStatement ps = db.conn().prepareStatement(
                "SELECT id FROM derived WHERE subject=? ORDER BY version")) {
            ps.setString(1, subject);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(derivedRow(rs.getString(1)));
            }
        } catch (Exception e) { throw new RuntimeException(e); }
        return out;
    }

    public List<Map<String, Object>> lastRecomputeReport() { return lastRecomputeReport; }

    /** 说明某项结果为何仍有效或为何需要重算（依赖图解释）。 */
    public Map<String, Object> explain(String subject) {
        State s = state();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("subject", subject);
        String derivedId = s.publishedAnalysis.get(subject);
        if (derivedId == null) {
            out.put("status", "no-published-analysis");
            out.put("invalidatedReason", s.invalidated.get(subject));
            return out;
        }
        JsonNode row = derivedRow(derivedId);
        JsonNode deps = row.get("deps");
        out.put("derivedId", derivedId);
        out.put("status", row.get("status").asText());
        out.put("version", row.get("version").asLong());
        String segmentId = deps.get("segmentId").asText();
        Models.Segment seg = s.segments.get(segmentId);
        String sampleId = seg == null ? null : seg.sampleId();
        List<Map<String, Object>> depRows = new ArrayList<>();
        depRows.add(depRow("segment", deps.get("segmentVersion").asLong(), 1L, "仪器输出段不可变"));
        Models.MassVersion mv = sampleId == null ? null : s.currentMass(sampleId);
        depRows.add(depRow("mass", deps.get("massVersion").asLong(),
                mv == null ? -1L : mv.version(), "质量版本（修订后归一化派生过期）"));
        Models.Calibration cal = sampleId == null ? null : s.calibrations.get(sampleId);
        depRows.add(depRow("calibration", deps.get("calibrationVersion").asLong(),
                cal == null ? 0L : cal.version(), "温度标定版本"));
        Models.BaselineCandidate adopted = adoptedFor(s, segmentId, kindOf(subject));
        depRows.add(depRow("baseline", deps.get("baselineId").asText(),
                adopted == null ? "(none)" : adopted.id(), "已采纳基线"));
        out.put("dependencies", depRows);
        boolean stale = depRows.stream().anyMatch(r -> !Boolean.TRUE.equals(r.get("matches")));
        out.put("stale", stale);
        out.put("conclusion", stale ? "输入版本不一致：需要重算" : "全部依赖版本一致：仍有效");
        return out;
    }

    private static Map<String, Object> depRow(String name, Object used, Object now, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("usedVersion", used);
        m.put("currentVersion", now);
        m.put("matches", Objects.equals(used, now));
        m.put("label", label);
        return m;
    }

    private static String kindOf(String subject) {
        return subject.substring(subject.indexOf(':') + 1);
    }

    static Models.BaselineCandidate adoptedFor(State s, String segmentId, String kind) {
        for (Models.BaselineCandidate b : s.baselines.values()) {
            if (b.segmentId().equals(segmentId) && b.kind().name().equals(kind) && b.adopted()) return b;
        }
        return null;
    }

    // ---------- 事务与冲突 ----------

    @FunctionalInterface
    private interface TxBody { void run(State snapshot) throws Exception; }

    /**
     * 乐观并发：baseSeq 为调用方看到的版本。其后的冲突事件若触及相同对象，
     * 双方都保留（conflicts 表 + conflict.raised 事件），抛 ConflictException 等待解决。
     */
    private void mutate(long baseSeq, String subject, TxBody body) {
        try {
            db.conn().setAutoCommit(false);
            long cur = db.currentSeq();
            if (cur != baseSeq) {
                List<EventStore.Event> recent = events.readRange(baseSeq, cur);
                for (EventStore.Event later : recent) {
                    if (subjectsOverlap(subject, later)) {
                        db.conn().rollback();
                        db.conn().setAutoCommit(true);
                        raiseConflict(subject, baseSeq, later);
                    }
                }
            }
            State snapshot = Projector.project(events.readAll());
            body.run(snapshot);
            db.conn().commit();
        } catch (RuntimeException re) {
            rollback();
            throw re;
        } catch (Exception e) {
            rollback();
            throw new RuntimeException(e);
        } finally {
            quietAutoCommit();
        }
    }

    private void rollback() {
        try { db.conn().rollback(); } catch (Exception ignored) {}
    }

    private void quietAutoCommit() {
        try { db.conn().setAutoCommit(true); } catch (Exception ignored) {}
    }

    private boolean subjectsOverlap(String subject, EventStore.Event later) {
        JsonNode p = later.payload();
        for (String key : List.of("sampleId", "segmentId", "subject", "targetId", "baselineId")) {
            if (p.hasNonNull(key)) {
                String t = p.get(key).asText();
                if (subject.equals(t) || subject.startsWith(t + ":") || t.startsWith(subject + ":")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void raiseConflict(String subject, long baseSeq, EventStore.Event later) {
        String conflictId = "conflict-" + UUID.randomUUID();
        try {
            db.conn().setAutoCommit(false);
            ObjectNode diag = EventStore.obj();
            diag.put("diagId", "diag-" + UUID.randomUUID());
            diag.put("code", "CONCURRENT_EDIT");
            diag.put("subject", subject);
            diag.put("message", "两位使用者从同一版本编辑 " + subject + "：冲突双方均已保留，需人工解决");
            diag.put("severity", "warning");
            events.append(EventTypes.DIAGNOSTIC_RAISED, "system", diag);
            ObjectNode p = EventStore.obj();
            p.put("conflictId", conflictId);
            p.put("subject", subject);
            p.put("baseSeq", baseSeq);
            p.put("eventA", later.id());
            p.put("eventB", "(pending-branch)");
            events.append(EventTypes.CONFLICT_RAISED, "system", p);
            try (PreparedStatement ps = db.conn().prepareStatement(
                    "INSERT INTO conflicts(id,subject,base_seq,event_a,event_b,status,resolution_event) VALUES(?,?,?,?,?,'open',NULL)")) {
                ps.setString(1, conflictId);
                ps.setString(2, subject);
                ps.setLong(3, baseSeq);
                ps.setString(4, later.id());
                ps.setString(5, "{\"note\":\"rejected-pending-branch\"}");
                ps.executeUpdate();
            }
            db.conn().commit();
        } catch (Exception e) {
            rollback();
            throw new RuntimeException(e);
        } finally {
            quietAutoCommit();
        }
        throw new ConflictException(conflictId,
                "编辑冲突: " + subject + " 已被事件 " + later.id() + " 先行修改");
    }
