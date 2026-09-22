package org.materials.dsc.domain;

/**
 * 样品质量的一个版本。质量修订不覆盖旧版本；新版本生效后，
 * 所有以质量为输入的归一化派生变为过期（依赖图负责传播）。
 */
public record MassVersion(
        String id,
        String sampleId,
        int seq,
        Quantity mass,
        String reason,
        String createdAtIso,
        /** 在第几个事件之后生效（null 表示立刻，用于回放）。 */
        Long effectiveAfterEvent) {

    public double milligrams() {
        if (mass == null || !mass.isConvertible()) {
            throw new IllegalStateException("mass not convertible: " + (mass == null ? null : mass.diagnostic()));
        }
        return mass.canonical();
    }
}
