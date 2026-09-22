package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * User-selected tangent anchor windows for a glass transition baseline.
 * The baseline is two straight tangents (pre / post) joined over the transition window.
 * Temperature coordinates are in degC.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class TangentSpec {
    public String id;
    public String segmentId;
    public String intervalId;
    public double preFromT;
    public double preToT;
    public double postFromT;
    public double postToT;
    public int version = 1;
    public String supersedes;
    public String createdByEvent;

    public TangentSpec() {
    }

    public TangentSpec(String id, String segmentId, String intervalId,
                       double preFromT, double preToT, double postFromT, double postToT,
                       int version, String supersedes, String createdByEvent) {
        this.id = id;
        this.segmentId = segmentId;
        this.intervalId = intervalId;
        this.preFromT = preFromT;
        this.preToT = preToT;
        this.postFromT = postFromT;
        this.postToT = postToT;
        this.version = version;
        this.supersedes = supersedes;
        this.createdByEvent = createdByEvent;
    }
}
