package dsc.domain;

/** Temperature calibration archive (standards and applied offset). */
public record TempCalibration(
        String standard,
        Qty referenceTemp,
        Qty observedTemp,
        Qty appliedOffset,
        String certifiedAt) {

    public double offsetCelsius() {
        return appliedOffset.to("\u00b0C");
    }
}
