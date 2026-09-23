package org.matlab.dsc.domain;

/**
 * Reviewer-selected temperature interval (kelvin). A dragged selection on the
 * curve page becomes one of these; it is the input to baseline fitting.
 */
public record Interval(double tStartK, double tEndK) {
    public double widthK() {
        return tEndK - tStartK;
    }

    public boolean empty() {
        return widthK() <= 0;
    }

    public boolean contains(double tk) {
        return tk >= Math.min(tStartK, tEndK) && tk <= Math.max(tStartK, tEndK);
    }
}
