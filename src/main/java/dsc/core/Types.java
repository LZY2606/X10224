package dsc.core;

import java.util.ArrayList;
import java.util.List;

/** Shared small value types for the DSC core. */
public final class Types {

    private Types() {}

    /** Machine-readable diagnostic attached to a parse or a derived result. */
    public record Diag(String code, String message) {
        @Override public String toString() { return code + ": " + message; }
    }

    /** A single machine-readable enthalpy error-source entry. */
    public record ErrorSource(String code, String detail, double contributionJg) {}

    public enum Direction { HEATING, COOLING }

    public enum EventKind { GLASS, CRYSTALLIZATION, MELT }

    /** Raw point as archived: original strings preserved beside parsed values. */
    public record RawPoint(String rawTime, String rawTemp, String rawFlow,
                           Double timeS, Double tempC, Double flowMw, List<Diag> diags) {
        public boolean usable() { return timeS != null && tempC != null && flowMw != null; }
    }

    /** Canonical (calibrated, unit-normalized) series used only by the derived layer. */
    public static final class Series {
        public double[] timeS;
        public double[] tempC;
        public double[] flowMw;
        public final List<Diag> diags = new ArrayList<>();
        public int size() { return timeS == null ? 0 : timeS.length; }
    }

    /** A contiguous monotonic run segment (one heating or cooling half-cycle). */
    public record Segment(int index, Direction direction, int cycle,
                          int startRow, int endRow,
                          double tStartC, double tEndC, double rateKPerMin) {}

    /** Anchors of a user-selected tangent pair, temperatures in Celsius. */
    public record TangentAnchors(double preT1, double preT2, double postT1, double postT2) {}

    /** One baseline candidate; several plausible candidates are always kept. */
    public record BaselineCandidate(String id, String kind,
                                    double onsetT, double endsetT,
                                    double[] baseTemp, double[] baseFlow,
                                    String note) {}

    /** Result of analyzing one thermal event window. */
    public static final class Analysis {
        public String eventId;
        public EventKind kind;
        public Direction direction;
        public int cycle;
        public double windowStartC;
        public double windowEndC;
        public Double onsetT;
        public Double peakT;
        public Double endsetT;
        public Double peakFlowMw;
        public Double areaMj;          // raw, mass-independent
        public Double enthalpyJg;      // normalized by the mass version in force
        public String massVersion;     // mass archive version used for normalization
        public String adoptedBaselineId;
        public final List<BaselineCandidate> candidates = new ArrayList<>();
        public final List<ErrorSource> errorSources = new ArrayList<>();
        public final List<Diag> diags = new ArrayList<>();
        public boolean emptyWindow;
    }
}
