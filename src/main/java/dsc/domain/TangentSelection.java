package dsc.domain;

/**
 * 用户选定的切线（玻璃化台阶用）：前后切线段各两个锚点温度，单独存档。
 * 切线只属于指定扫描段，加热/冷却不混用。
 */
public record TangentSelection(
        String id, String segmentId,
        double preLeftC, double preRightC,
        double postLeftC, double postRightC,
        String actor
) {}
