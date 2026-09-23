package dsc.core;

/**
 * One heating or cooling segment. Arrays are aligned by index:
 * tK temperature in Kelvin, hfW heat flow in Watt, dtS time in seconds.
 * The segment direction is declared, not guessed.
 */
public record RawSeries(
        String seriesId,
        String segment,
        String direction,
        int cycle,
        HeatSign sign,
        double[] tK,
        double[] hfW,
        double[] dtS,
        boolean nonUniform
) {

    public int length() {
        return tK.length;
    }

    public boolean heating() {
        return "heat".equalsIgnoreCase(direction);
    }

    public boolean cooling() {
        return "cool".equalsIgnoreCase(direction);
    }
}
