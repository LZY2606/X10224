package dsc.model;

import java.util.List;

/** Temperature calibration record: standards and an additive correction (°C, normalized). */
public final class TempCalibration {
    private final String method;
    private final List<String> standards;
    private final RawField correctionC;
    private final String date;

    public TempCalibration(String method, List<String> standards, RawField correctionC, String date) {
        this.method = method == null ? "" : method;
        this.standards = standards == null ? List.of() : List.copyOf(standards);
        this.correctionC = correctionC;
        this.date = date;
    }

    public String method() {
        return method;
    }

    public List<String> standards() {
        return standards;
    }

    public RawField correctionC() {
        return correctionC;
    }

    public double correctionNormalizedC() {
        return correctionC != null && correctionC.normalized() != null ? correctionC.normalized() : 0.0;
    }

    public String date() {
        return date;
    }
}
