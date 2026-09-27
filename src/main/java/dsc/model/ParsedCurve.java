package dsc.model;

import java.util.ArrayList;
import java.util.List;

/** Result of parsing one instrument output: normalized-friendly points plus raw diagnostics. */
public record ParsedCurve(
        List<CurvePoint> points,
        List<String> diagnostics,
        String signConvention,
        String declaredScanDirection,
        String temperatureUnit,
        String heatFlowUnit
) {
    public ParsedCurve {
        points = points == null ? List.of() : List.copyOf(points);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public ParsedCurve withDiagnostic(String code) {
        List<String> next = new ArrayList<>(diagnostics);
        next.add(code);
        return new ParsedCurve(points, next, signConvention, declaredScanDirection,
                temperatureUnit, heatFlowUnit);
    }
}
