package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One instrument sample, carrying both the original textual representation (preserved on import)
 * and the parsed/normalised view used by the derived layer.
 *
 * <p>A point that cannot be parsed or converted is kept at its origin with diagnostics and excluded
 * from numerical derivation — it is never silently dropped from the archive or from the plot data.
 */
public final class SamplePoint {
    private final int index;
    private final String rawTime;
    private final String rawTemperature;
    private final String rawHeatFlow;
    private final Double timeMin;
    private final Double temperatureC;
    private final Double heatFlowMw;
    private final List<String> diagnostics = new ArrayList<>();

    public SamplePoint(int index, String rawTime, String rawTemperature, String rawHeatFlow,
                       Double timeMin, Double temperatureC, Double heatFlowMw) {
        this.index = index;
        this.rawTime = rawTime;
        this.rawTemperature = rawTemperature;
        this.rawHeatFlow = rawHeatFlow;
        this.timeMin = timeMin;
        this.temperatureC = temperatureC;
        this.heatFlowMw = heatFlowMw;
    }

    public int index() {
        return index;
    }

    public String rawTime() {
        return rawTime;
    }

    public String rawTemperature() {
        return rawTemperature;
    }

    public String rawHeatFlow() {
        return rawHeatFlow;
    }

    public Double timeMin() {
        return timeMin;
    }

    public Double temperatureC() {
        return temperatureC;
    }

    public Double heatFlowMw() {
        return heatFlowMw;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public boolean usable() {
        return timeMin != null && temperatureC != null && heatFlowMw != null;
    }

    /** Parses one textual instrument row, preserving the raw tokens and recording diagnostics. */
    public static SamplePoint parse(int index, String rawTime, String rawTemperature,
                                    String rawHeatFlow, String temperatureUnit, String heatFlowUnit) {
        List<String> problems = new ArrayList<>();
        Double timeMin = parseDouble(rawTime, "time", "min", problems);
        Double temperatureC = null;
        Double heatFlowMw = null;
        Double tempValue = parseDouble(rawTemperature, "temperature", temperatureUnit, problems);
        if (tempValue != null) {
            if (!Units.isTemperatureUnit(temperatureUnit)) {
                problems.add("temperature unit '" + temperatureUnit + "' is not convertible to °C");
            } else {
                try {
                    temperatureC = Units.toCelsius(tempValue, temperatureUnit);
                } catch (Units.ConversionException e) {
                    problems.add(e.getMessage());
                }
            }
        }
        Double heatValue = parseDouble(rawHeatFlow, "heat flow", heatFlowUnit, problems);
        if (heatValue != null) {
            if (Units.MW.equals(heatFlowUnit)) {
                heatFlowMw = heatValue;
            } else {
                problems.add("heat flow unit '" + heatFlowUnit + "' is not convertible to mW");
            }
        }
        SamplePoint point = new SamplePoint(index, rawTime, rawTemperature, rawHeatFlow,
                timeMin, temperatureC, heatFlowMw);
        point.diagnostics.addAll(problems);
        return point;
    }

    private static Double parseDouble(String raw, String field, String unit, List<String> problems) {
        if (raw == null || raw.isBlank()) {
            problems.add(field + " is empty");
            return null;
        }
        String token = raw.trim();
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            problems.add(field + " value '" + token + "' is not numeric (unit " + unit + ")");
            return null;
        }
    }
}
