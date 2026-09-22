package dsc.domain;

import java.util.List;

/** Lightweight references to the archived source versions a derivation depends on. */
public final class SourceRefs {
    private SourceRefs() {
    }

    public record Mass(String massId, double valueMg) {
    }

    public record ScanRate(String rateId, double signedKPerMin) {
    }

    public record Atmosphere(String atmosphereId) {
    }

    public record TempCalibration(String calibrationId, double offsetC, double uncertaintyC) {
    }

    public record Tangents(String tangentId, String segmentId, String intervalId, String method,
                           List<double[]> leftTangent, List<double[]> rightTangent) {
    }
}
