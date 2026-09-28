package com.materials.dsc.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Onset/peak/endset and enthalpy (with normalised form), plus the error
 * source list used on the review page.  Each display value carries a stable
 * identity so export -> delete DB -> import keeps identity/order/provenance.
 */
public final class Transition {
    public final String id;
    public final String analysisId;
    public final String type;
    public final double onset, peak, endset;
    public final double enthalpyMj;
    public final Double enthalpyJPerG;
    public final double cpStep;
    public final String baselineCandidateId;
    public final String status;
    public ObjectNode detail;

    public Transition(String id, String analysisId, String type,
                      double onset, double peak, double endset,
                      double enthalpyMj, Double enthalpyJPerG, double cpStep,
                      String baselineCandidateId, String status) {
        this.id = id;
        this.analysisId = analysisId;
        this.type = type;
        this.onset = onset;
        this.peak = peak;
        this.endset = endset;
        this.enthalpyMj = enthalpyMj;
        this.enthalpyJPerG = enthalpyJPerG;
        this.cpStep = cpStep;
        this.baselineCandidateId = baselineCandidateId;
        this.status = status;
        this.detail = com.materials.dsc.util.Json.obj();
    }
}
