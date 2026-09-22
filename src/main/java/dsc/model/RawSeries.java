package dsc.model;

import dsc.units.Quantity;

import java.util.List;

public record RawSeries(
        String segmentId,
        String blobId,
        int rowCount,
        List<Double> temperatureK,
        List<Double> heatFlowW,
        List<Double> timeS,
        String temperatureUnit,
        String heatFlowUnit,
        String timeUnit,
        String convention,
        List<Integer> unconvertibleRows,
        List<String> columnDiagnostics
) {
    public static final String ENDO_UP = "endo-up";
    public static final String EXO_UP = "exo-up";

    public double[] heatFlowNormalizedW() {
        double[] out = new double[heatFlowW.size()];
        boolean flip = EXO_UP.equalsIgnoreCase(convention);
        for (int i = 0; i < out.length; i++) {
            Double v = heatFlowW.get(i);
            out[i] = v == null ? Double.NaN : flip ? -v : v;
        }
        return out;
    }

    public double[] tempArray() {
        double[] out = new double[temperatureK.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = temperatureK.get(i);
        }
        return out;
    }

    public double[] timeArray() {
        double[] out = new double[timeS.size()];
        for (int i = 0; i < out.length; i++) {
            Double v = timeS.get(i);
            out[i] = v == null ? Double.NaN : v;
        }
        return out;
    }
}
