package dsc.model;

import dsc.core.Quantity;
import dsc.core.RawValue;

import java.util.ArrayList;
import java.util.List;

/**
 * One instrument sample. Raw tokens are preserved verbatim. A point that cannot
 * be converted stays in the trace with a diagnostic; it is never silently
 * dropped to keep the plottable series clean.
 */
public final class SamplePoint {
    private final int index;
    private final RawValue temperature;
    private final RawValue heatFlow;
    private final RawValue time; // may be null: time reconstructed from rate
    private final List<String> diagnostics = new ArrayList<>();
    private Quantity tCelsius;
    private Quantity hfMilliwatts;
    private Quantity timeSeconds;

    public SamplePoint(int index, RawValue temperature, RawValue heatFlow, RawValue time) {
        this.index = index;
        this.temperature = temperature;
        this.heatFlow = heatFlow;
        this.time = time;
    }

    public int index() { return index; }
    public RawValue temperature() { return temperature; }
    public RawValue heatFlow() { return heatFlow; }
    public RawValue timeToken() { return time; }
    public List<String> diagnostics() { return diagnostics; }

    /** Parse and normalize. Returns false (and records diagnostics) instead of throwing. */
    public boolean materialize() {
        boolean ok = true;
        var tp = temperature.parse();
        if (tp.ok()) {
            tCelsius = tp.quantity().to(dsc.core.Units.C);
        } else { diagnostics.add("temperature[" + index + "]: " + tp.diagnostic()); ok = false; }
        var hp = heatFlow.parse();
        if (hp.ok()) {
            hfMilliwatts = hp.quantity().to(dsc.core.Units.MW);
        } else { diagnostics.add("heatFlow[" + index + "]: " + hp.diagnostic()); ok = false; }
        if (time != null) {
            var wp = time.parse();
            if (wp.ok()) timeSeconds = wp.quantity(); // declared unit kept as-is, seconds canonical expected
            else { diagnostics.add("time[" + index + "]: " + wp.diagnostic()); }
        }
        return ok;
    }

    public Quantity tCelsius() { return tCelsius; }
    public Quantity hfMilliwatts() { return hfMilliwatts; }
    public Quantity timeSeconds() { return timeSeconds; }
    public boolean plottable() { return tCelsius != null && hfMilliwatts != null; }
}
