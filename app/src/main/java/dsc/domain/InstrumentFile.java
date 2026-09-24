package dsc.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured view of an instrument-output file. Header lines are kept verbatim and the original
 * numeric representations are retained; normalization happens later in the derivation layer.
 */
public class InstrumentFile {
    public String fileName;
    public String instrument;
    public String method;
    public Enums.SegmentRole role = Enums.SegmentRole.UNKNOWN;
    public Enums.Direction direction = Enums.Direction.UNKNOWN;
    public Enums.SignConvention signConvention = Enums.SignConvention.UNKNOWN;
    public String tUnit;
    public String hfUnit;
    public double declaredRate = Double.NaN;   // signed as declared; NaN when absent
    public String rateUnit;
    public String gas;
    public double gasFlow = Double.NaN;
    public String gasFlowUnit;
    public List<CurvePoint> points = new ArrayList<>();
    public List<String> rawHeader = new ArrayList<>();
    public List<String> diagnostics = new ArrayList<>();
}
