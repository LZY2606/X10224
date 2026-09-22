package org.materials.dsc.domain;

import java.util.List;

/** 样品履历：所有加热/冷却序列挂在同一样品下。 */
public record Sample(
        String id,
        String materialId,
        String name,
        String createdAtIso,
        List<String> diagnostics) {
}
