package dsc.engine;

import java.util.List;

/** Result of analysing one dragged interval: events, baselines, integration and error budget. */
public record IntervalMetrics(String intervalId, String segmentId, String kind,
                              double t0, double t1,
                              Double onset, Double peak, Double endset,
                              Double tgMidpoint,
                              List<BaselineCandidate> baselines,
                              String acceptedMethod,
                              List<ErrorTerm> errorBudget,
                              List<String> diagnostics,
                              String status) {

    public record BaselineCandidate(String method, String label, double[] t, double[] y,
                                    double areaJg, double deltaHJg, int sign,
                                    double onset, double peak, double endset,
                                    List<ErrorTerm> errorBudget,
                                    List<String> diagnostics) {
    }

    public record ErrorTerm(String code, String source, double plusJg, double minusJg) {
    }
}
