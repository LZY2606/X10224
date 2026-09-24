package dsc;

/**
 * A user-selected tangent line y = slope*x + intercept, chosen against
 * canonical coordinates (K, W). Provenance points back to the archived
 * tangent source document and the analyst who selected it.
 */
public record Tangent(
        String tangentId,
        String segmentId,
        String side,             // "pre" | "post" | "lower" | "upper"
        double t1K, double y1W,
        double t2K, double y2W,
        String sourceDocId
) {
    public double slope() {
        return (y2W - y1W) / (t2K - t1K);
    }

    public double intercept() {
        return y1W - slope() * t1K;
    }

    public double evaluate(double tK) {
        return slope() * tK + intercept();
    }
}
