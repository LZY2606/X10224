package org.materials.dsc.domain;

/**
 * 事件热效应方向。由规范符号下的净热流判定，不由文件名/段名猜测。
 */
public enum ThermalSign {
    ENDOTHERMIC("endothermic"), EXOTHERMIC("exothermic"), AMBIGUOUS("ambiguous");

    private final String label;

    ThermalSign(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
