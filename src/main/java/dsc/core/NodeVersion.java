package dsc.core;

import java.util.List;
import java.util.Map;

/**
 * 一个节点的一次不可变版本。依赖以 (kind,nodeId,version) 三元组钉死，
 * 因此回到任意旧事件时恢复的是当时看到的整棵依赖版本树。
 */
public final class NodeVersion {
    public final String nodeId;
    public final GraphNodeKind kind;
    public final int version;
    public final List<DepRef> dependencies;
    public final String payloadJson;
    public final String status;       // OK | ERROR | AMBIGUOUS | STALE
    public final String diagnostic;
    public final String ruleVersion;
    public final long createdAtSeq;

    public static final class DepRef {
        public final GraphNodeKind kind;
        public final String nodeId;
        public final int version;

        public DepRef(GraphNodeKind kind, String nodeId, int version) {
            this.kind = kind;
            this.nodeId = nodeId;
            this.version = version;
        }
    }

    public NodeVersion(String nodeId, GraphNodeKind kind, int version, List<DepRef> dependencies,
                       String payloadJson, String status, String diagnostic, String ruleVersion,
                       long createdAtSeq) {
        this.nodeId = nodeId;
        this.kind = kind;
        this.version = version;
        this.dependencies = List.copyOf(dependencies);
        this.payloadJson = payloadJson;
        this.status = status;
        this.diagnostic = diagnostic;
        this.ruleVersion = ruleVersion;
        this.createdAtSeq = createdAtSeq;
    }

    public Map<String, Object> ref() {
        return Map.of("nodeId", nodeId, "kind", kind.code, "version", version);
    }
}
