package dsc.derive;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dsc.domain.Json;

/**
 * Immutable normalized views of the dossier content used by the engine.
 * Sign rule: normalized heat flow is ENDOTHERMIC-UP. Conversion uses ONLY the sign
 * convention declared by the instrument header; an undeclared convention yields a
 * diagnostic curve instead of a guessed sign.
 */
public final class Models {
    private Models() {}

    /** A scan segment within one sample history (e.g. heat1, cool1, heat2). */
    public static final class Segment {
        public final String segmentId;
        public final String cycle;                 // "cycle-1" ...
        public final String direction;             // heating | cooling
        public final double rateKPerMin;
        public final String declaredSign;          // endo-up | endo-down | undeclared
        public final double[] tRaw, hfRaw;         // raw as-imported samples
        public final double[] tK, hfEndoUp;        // normalized (K, W/g endo-up); hf NaN if undeclared
        public final double[] dt;                  // temperature gaps, for non-uniform detection
        public final boolean nonUniform;
        public final java.util.List<String> diagnostics;

        public Segment(String segmentId, String cycle, String direction, double rate, String declaredSign,
                       double[] tRaw, double[] hfRaw, double[] tK, double[] hfEndoUp,
                       boolean nonUniform, java.util.List<String> diagnostics) {
            this.segmentId = segmentId; this.cycle = cycle; this.direction = direction;
            this.rateKPerMin = rate; this.declaredSign = declaredSign;
            this.tRaw = tRaw; this.hfRaw = hfRaw; this.tK = tK; this.hfEndoUp = hfEndoUp;
            this.dt = new double[Math.max(0, tK.length - 1)];
            for (int i = 1; i < tK.length; i++) dt[i-1] = tK[i]-tK[i-1];
            this.nonUniform = nonUniform; this.diagnostics = diagnostics;
        }
    }

    /** User-selected interval with stored tangent anchors (a direct input, archived). */
    public static final class Interval {
        public final String id, version, segmentId;
        public final double loK, hiK;
        public final double leftAnchorK, rightAnchorK; // tangent points chosen by reviewer
        public Interval(String id, String version, String segmentId, double loK, double hiK,
                        double leftAnchorK, double rightAnchorK) {
            this.id = id; this.version = version; this.segmentId = segmentId;
            this.loK = loK; this.hiK = hiK; this.leftAnchorK = leftAnchorK; this.rightAnchorK = rightAnchorK;
        }
    }

    /** Baseline evaluated at curve temperatures, with method-specific parameters. */
    public static final class Baseline {
        public final String method;
        public final double[] values;   // endo-up W/g baseline over interval temperature grid
        public final ObjectNode params;
        public Baseline(String method, double[] values, ObjectNode params) {
            this.method = method; this.values = values; this.params = params;
        }
    }
}
