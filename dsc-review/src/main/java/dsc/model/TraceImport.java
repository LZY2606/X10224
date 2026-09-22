package dsc.model;

import dsc.core.RawValue;
import dsc.core.Unit;
import dsc.core.Units;

import java.util.ArrayList;
import java.util.List;

/**
 * A parsed instrument-output fragment: raw CSV rows retained verbatim before
 * normalization. This is what TRACE_IMPORTED carries; the reducer materializes
 * a {@link ScanSegment} from it while keeping raw tokens and diagnostics.
 */
public final class TraceImport {
    public String segmentId;
    public String sourceFile;
    public int cycle;
    public String conventionRaw = "unknown";
    public double declaredRate;
    public Unit rateUnit = Units.C_PER_MIN;
    public List<Row> rows = new ArrayList<>();

    public static final class Row {
        public String temperature;
        public String heatFlow;
        public String time;

        public Row() {}
        public Row(String temperature, String heatFlow, String time) {
            this.temperature = temperature;
            this.heatFlow = heatFlow;
            this.time = time;
        }
    }

    public ScanSegment toSegment() {
        List<SamplePoint> pts = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            RawValue t = new RawValue(r.temperature, Units.C);
            RawValue h = new RawValue(r.heatFlow, Units.MW);
            RawValue w = r.time == null || r.time.isBlank() ? null : new RawValue(r.time, Units.ONE);
            SamplePoint p = new SamplePoint(i, t, h, w);
            p.materialize();
            pts.add(p);
        }
        return new ScanSegment(segmentId, sourceFile, cycle,
                SignalConvention.parse(conventionRaw),
                new dsc.core.Quantity(declaredRate, rateUnit), pts);
    }
}
