package dsc.model;

/**
 * A value shown in the review UI together with its stable identity and the
 * coordinate basis / unit it is expressed in. Every number the curve page
 * reads back from the archive is a DisplayValue, so screens never invent an
 * identity for a computed result.
 */
public record DisplayValue(String id, String label, double value, String unit,
                           String basis, int eventSeq, String derivedFrom) {
}
