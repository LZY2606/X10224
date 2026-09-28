package com.materials.dsc.model;

/**
 * One raw instrument sample.  Temperatures and heat flow keep both their raw
 * tokens and their parsed values; x coordinates are the actual (possibly
 * non-equally-spaced) instrument temperatures, never regridded indices.
 */
public final class Point {
    public final String tempRaw;
    public final double tempC;
    public final String hfRaw;
    public final Double hfMw;
    public final String timeRaw;
    public final Double timeS;
    public final String tempDiag;
    public final String hfDiag;

    public Point(String tempRaw, double tempC, String hfRaw, Double hfMw,
                 String timeRaw, Double timeS, String tempDiag, String hfDiag) {
        this.tempRaw = tempRaw;
        this.tempC = tempC;
        this.hfRaw = hfRaw;
        this.hfMw = hfMw;
        this.timeRaw = timeRaw;
        this.timeS = timeS;
        this.tempDiag = tempDiag;
        this.hfDiag = hfDiag;
    }
}
