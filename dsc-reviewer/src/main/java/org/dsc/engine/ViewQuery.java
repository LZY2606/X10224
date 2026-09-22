package org.dsc.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.dsc.domain.Models.*;
import org.dsc.store.EventRecord;
import org.dsc.store.Store;

import java.util.List;

/**
 * 复核界面读模型。每个显示值带稳定身份（entityId/version/resultId），
 * atEvent 非空时做时间旅行：回到当时事件，恢复当时依赖版本与可见结论。
 */
public final class ViewQuery {
    private final Store store;

    public ViewQuery(Store store) { this.store = store; }

    public ObjectNode caseView(String caseId, String atEvent) {
        ObjectNode root = EventRecord.MAPPER.createObjectNode();
        root.put("caseId", caseId);
        root.put("atEvent", atEvent == null ? "" : atEvent);
        ArrayNode events = root.putArray("events");
        try {
            for (EventRecord e : store.events(caseId)) {
                ObjectNode ev = EventRecord.MAPPER.createObjectNode();
                ev.put("eventId", e.eventId());
                ev.put("parentId", e.parentId() == null ? "" : e.parentId());
                ev.put("seq", e.seq());
                ev.put("type", e.type());
                ev.put("actor", e.actor());
                ev.put("at", e.at().toString());
                ev.set("payload", e.payload());
                events.add(ev);
                if (atEvent != null && e.eventId().equals(atEvent)) break;
            }
            root.set("segments", collect(caseId, "SEGMENT", atEvent));
            root.set("baselines", collect(caseId, "BASELINE", atEvent));
            root.set("results", collect(caseId, "RESULT", atEvent));
            root.set("masses", collect(caseId, "MASS", atEvent));
            root.set("tangents", collect(caseId, "TANGENT", atEvent));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return root;
    }

    private ArrayNode collect(String caseId, String kind, String atEvent) throws Exception {
        ArrayNode arr = EventRecord.MAPPER.createArrayNode();
        for (Store.EntityRow r : store.entitiesByKind(caseId, kind)) {
            Store.EntityRow vis;
            if (atEvent == null) {
                Store.EntityRow cur = store.currentEntity(r.entityId()).orElse(null);
                if (cur == null || cur.version() != r.version()) continue;
                vis = r;
            } else {
                if (r.version() != store.entityVisibleAt(r.entityId(), atEvent)
                        .map(Store.EntityRow::version).orElse(-1)) continue;
                vis = store.entityVisibleAt(r.entityId(), atEvent).orElseThrow();
            }
            ObjectNode n = EventRecord.MAPPER.createObjectNode();
            n.put("id", vis.entityId());
            n.put("version", vis.version());
            n.put("kind", vis.kind());
            n.put("label", vis.label());
            n.put("status", vis.status());
            n.put("createdEvent", vis.createdEvent());
            n.set("value", vis.json());
            arr.add(n);
        }
        return arr;
    }

    /** 来源链：某结果 -> 基线 -> 段 -> 直接来源（含版本），供“异常点进入来源链”。 */
    public ObjectNode provenance(String resultId, String atEvent) {
        ObjectNode out = EventRecord.MAPPER.createObjectNode();
        try {
            Store.EntityRow res = (atEvent == null ? store.currentEntity(resultId)
                    : store.entityVisibleAt(resultId, atEvent)).orElseThrow();
            out.put("resultId", res.entityId());
            out.put("resultVersion", res.version());
            out.set("result", res.json());
            String blId = res.json().path("baselineId").asText(null);
            if (blId != null) {
                Store.EntityRow bl = (atEvent == null ? store.currentEntity(blId)
                        : store.entityVisibleAt(blId, atEvent)).orElseThrow();
                ObjectNode b = EventRecord.MAPPER.createObjectNode();
                b.put("id", bl.entityId());
                b.put("version", bl.version());
                b.set("value", bl.json());
                out.set("baseline", b);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return out;
    }
}
