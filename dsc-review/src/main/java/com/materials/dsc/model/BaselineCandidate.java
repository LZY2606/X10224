package com.materials.dsc.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A legitimate baseline alternative.  Every reasonable candidate is kept,
 * including ones the reviewer did not adopt; rejecting a candidate must not
 * destroy it.
 */
public final class BaselineCandidate {
    public final String id;
    public final String analysisId;
    public final String method;
    public final String rangeId;
    public final double t0, t1;
    public final double[] lineT;
    public final double[] lineY;
    public final String rationale;
    public final String status;
    public final int nodeVersion;
    public ObjectNode metrics;

    public BaselineCandidate(String id, String analysisId, String method, String rangeId,
                             double t0, double t1, double[] lineT, double[] lineY,
                             String rationale, String status, int nodeVersion) {
        this.id = id;
        this.analysisId = analysisId;
        this.method = method;
        this.rangeId = rangeId;
        this.t0 = t0;
        this.t1 = t1;
        this.lineT = lineT;
        this.lineY = lineY;
        this.rationale = rationale;
        this.status = status;
        this.nodeVersion = nodeVersion;
        this.metrics = com.materials.dsc.util.Json.obj();
    }
}
