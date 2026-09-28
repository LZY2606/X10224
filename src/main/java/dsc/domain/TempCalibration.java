package dsc.domain;

/**
 * Temperature calibration applied in the derivation layer only:
 * T_calibrated = gain * T_raw + offset. The raw curve is never rewritten.
 */
public final class TempCalibration {
    public final String id;
    public final int version;
    public final double gain;
    public final double offsetDegC;
    public final String standard;

    public TempCalibration(String id, int version, double gain, double offsetDegC, String standard) {
        this.id = id;
        this.version = version;
        this.gain = gain;
        this.offsetDegC = offsetDegC;
        this.standard = standard;
    }
}
