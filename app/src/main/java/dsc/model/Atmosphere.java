package dsc.model;

/** Gas atmosphere with optional flow. Flow normalization is L/min; raw text is preserved. */
public final class Atmosphere {
    private final String gas;
    private final RawField flow;

    public Atmosphere(String gas, RawField flow) {
        this.gas = gas == null ? "" : gas;
        this.flow = flow;
    }

    public String gas() {
        return gas;
    }

    public RawField flow() {
        return flow;
    }
}
