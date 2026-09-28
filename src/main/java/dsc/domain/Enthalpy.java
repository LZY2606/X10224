package dsc.domain;

import java.util.List;

/**
 * Integrated area between curve and baseline. rawJ is instrument power-like area;
 * specificJg needs the mass revision. Error sources are enumerated explicitly.
 */
public final class Enthalpy {
    public final String id;
    public final String baselineId;
    public final String transitionId;
    public final double rawJ;
    public final Double specificJg;
    public final String massRevisionId;
    public final List<String> errorSources;
    public final double trapezoidUncertaintyJ;

    public Enthalpy(String id, String baselineId, String transitionId, double rawJ,
                   Double specificJg, String massRevisionId, List<String> errorSources,
                   double trapezoidUncertaintyJ) {
        this.id = id;
        this.baselineId = baselineId;
        this.transitionId = transitionId;
        this.rawJ = rawJ;
        this.specificJg = specificJg;
        this.massRevisionId = massRevisionId;
        this.errorSources = List.copyOf(errorSources);
        this.trapezoidUncertaintyJ = trapezoidUncertaintyJ;
    }
}
