package dsc.domain;

/**
 * Raw and normalized coordinate of one instrument-output sample.
 * Coordinate frame of {@code tC}/{@code hfWg} is fixed: °C and W/g; {@code tRaw}/{@code hfRaw}
 * keep the original representation. When conversion fails the normalized fields are NaN and a
 * diagnostic is attached — the point is never dropped just to make the chart render.
 */
public class CurvePoint {
    public String segmentId;
    public int idx;
    public double tRaw;
    public String tRawUnit;
    public double hfRaw;
    public String hfRawUnit;
    public double tC = Double.NaN;
    public double hfWg = Double.NaN;
    public boolean convertible;
}
