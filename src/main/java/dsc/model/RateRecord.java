package dsc.model;

import dsc.units.Quantity;

public record RateRecord(String segmentId, Quantity measuredRateKPerS, Quantity declaredRateKPerS, String source,
                         String eventId) {
}
