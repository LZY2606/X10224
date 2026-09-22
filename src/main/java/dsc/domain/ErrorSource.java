package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One contribution to the uncertainty/error budget of a derived enthalpy or temperature.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ErrorSource {
    public String id;
    public String code;
    public String label;
    public double contribution;
    public String unit;
    public String evidence;

    public ErrorSource() {
    }

    public ErrorSource(String id, String code, String label, double contribution,
                       String unit, String evidence) {
        this.id = id;
        this.code = code;
        this.label = label;
        this.contribution = contribution;
        this.unit = unit;
        this.evidence = evidence;
    }
}
