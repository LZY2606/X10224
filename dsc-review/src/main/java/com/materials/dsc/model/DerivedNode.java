package com.materials.dsc.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One immutable derivation version.  The current pointer for a node key moves
 * to a new row only after that row committed completely; partial writes can
 * never be observed.
 */
public final class DerivedNode {
    public final long id;
    public final String nodeKey;
    public final int version;
    public final String kind;
    public final String scopeSegmentId;
    public final String basis;
    public final String inputHash;
    public final String depsHash;
    public final JsonNode result;
    public final String status;
    public final String originEventId;
    public final boolean current;

    public DerivedNode(long id, String nodeKey, int version, String kind, String scopeSegmentId,
                       String basis, String inputHash, String depsHash, JsonNode result,
                       String status, String originEventId, boolean current) {
        this.id = id;
        this.nodeKey = nodeKey;
        this.version = version;
        this.kind = kind;
        this.scopeSegmentId = scopeSegmentId;
        this.basis = basis;
        this.inputHash = inputHash;
        this.depsHash = depsHash;
        this.result = result;
        this.status = status;
        this.originEventId = originEventId;
        this.current = current;
    }
}
