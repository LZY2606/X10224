package dsc.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

/**
 * One instrument output sample, kept exactly as imported.
 * temperature/heatFlow/time carry both the original representation and (when possible)
 * a canonical numeric value. Non-convertible cells stay at their index with diagnostics
 * instead of being dropped for plotting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class RawPoint {
    public int index;
    public Quantity temperature;
    public Quantity heatFlow;
    public Quantity time;
    public final List<String> diagnostics = new ArrayList<>();

    public RawPoint() {
    }

    public RawPoint(int index, String temp, String tempUnit, String hf, String hfUnit,
                    String time, String timeUnit) {
        this.index = index;
        this.temperature = new Quantity(temp, tempUnit);
        this.heatFlow = new Quantity(hf, hfUnit);
        this.time = new Quantity(time, timeUnit);
        if (!temperature.convertible) {
            diagnostics.add("temperature@" + index + ": " + temperature.diagnostic);
        }
        if (!heatFlow.convertible) {
            diagnostics.add("heatFlow@" + index + ": " + heatFlow.diagnostic);
        }
        if (!time.convertible) {
            diagnostics.add("time@" + index + ": " + time.diagnostic);
        }
    }

    public boolean clean() {
        return diagnostics.isEmpty();
    }
}
