package dsc.model;

/** One versioned scan-rate statement for a segment, with rate uncertainty. */
public record ScanRateVersion(String rateId, String sequenceId, double value, String unit,
                              double uncertaintyKPerMin, int revision) {
    public double kPerMin() {
        if (!Quantity.K_PER_MIN.equals(unit))
            throw new IllegalArgumentException("unsupported rate unit: " + unit);
        return value;
    }
}
