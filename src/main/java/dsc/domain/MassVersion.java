package dsc.domain;

/** 样品质量版本：质量修订产生新版本，归一化派生随之过期。 */
public record MassVersion(String id, int seq, RawValue massMg, String reason, String createdAt) {
    public double mg() { return massMg.normalized(); }
}
