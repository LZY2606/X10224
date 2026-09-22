package dsc.domain;

/**
 * 样品质量版本。质量是独立存档的来源文档；修订生成新版本（massVersion+1），
 * 不覆盖旧值。所有质量归一化派生（W/g 曲线、J/g 焓变、台阶高）都带
 * massVersion；质量修订后这些派生全部过期，必须用新质量重算。
 */
public final class MassVersion {
    public final String massId;
    public final int massVersion;
    public final double milligrams;
    public final String rawText;       // 原始表示，例如 "12.40 mg"
    public final String recordedAt;
    public final String note;
    public final String supersedesMassId;

    public MassVersion(String massId, int massVersion, double milligrams, String rawText,
                       String recordedAt, String note, String supersedesMassId) {
        this.massId = massId;
        this.massVersion = massVersion;
        this.milligrams = milligrams;
        this.rawText = rawText;
        this.recordedAt = recordedAt;
        this.note = note;
        this.supersedesMassId = supersedesMassId;
    }

    public double grams() {
        return milligrams / 1000.0;
    }
}
