package dsc.domain;

import dsc.domain.Enums.*;
import java.util.ArrayList;
import java.util.List;

/**
 * One legal baseline for one interval. Multiple candidates coexist: an accepted
 * candidate never overwrites the alternates, and all carry their inputs so the
 * difference between them stays auditable.
 */
public final class BaselineCandidate {
    public String candidateId;
    public String traceId;
    public TransitionType transitionType;
    public BaselineMethod method;
    public Interval intervalC;
    public List<String> anchorRowKeys = new ArrayList<>();
    public Line line;                 // chord / step baseline evaluated over interval
    public Line preTangent, postTangent;
    public Double onsetC, endsetC;
    public List<ErrorSource> errorSources = new ArrayList<>();
    public CandidateStatus status = CandidateStatus.ALTERNATE;
    public String decidedByEventId;
    public String ruleVersion;
    public String rationale;
    public boolean computable = true;

    public BaselineCandidate() {}
}
