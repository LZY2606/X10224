package dsc.model;

/**
 * 样品质量是独立存档的直接来源，自带版本。质量修订后，
 * 所有以质量为依赖的归一化派生（J/g 焓变、质量归一热流）标记过期。
 */
public record MassRecord(String massId,
                         int version,
                         Quantity mass,
                         double milligrams,
                         String note,
                         String supersedesMassId) {

    public static MassRecord create(String massId, int version, Quantity mass, String note, String supersedes) {
        return new MassRecord(massId, version, mass,
                Units.massToMilligram(mass.value(), mass.unit()), note, supersedes);
    }
}
