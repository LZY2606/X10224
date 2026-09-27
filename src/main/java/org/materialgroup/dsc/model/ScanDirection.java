package org.materialgroup.dsc.model;

/**
 * 扫描段方向。仅允许来自仪器程序段元数据或由有序温度趋势判定，
 * 不允许由文件名（如 *_heat.csv）推断。
 */
public enum ScanDirection {
    HEATING("加热"),
    COOLING("冷却"),
    ISOTHERMAL("恒温"),
    /** 无法由元数据或单调温度趋势唯一判定。 */
    UNDETERMINED("无法判定");

    private final String label;

    ScanDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
