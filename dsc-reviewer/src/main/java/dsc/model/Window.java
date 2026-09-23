package dsc.model;

/**
 * An analysis interval on one trace, in trace-native temperature units (K or
 * degC, both use kelvin-sized degrees; the engine works in the stored unit).
 */
public record Window(String id, int version, String traceId,
                     double tLow, double tHigh, String unit, String createdBy, String importedAt) {

    public boolean empty() {
        return !(tHigh > tLow);
    }

    public Window moved(double lo, double hi) {
        return new Window(id, version + 1, traceId, lo, hi, unit, createdBy, importedAt);
    }
}
