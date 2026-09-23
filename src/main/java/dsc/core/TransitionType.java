package dsc.core;

public enum TransitionType {
    GLASS("玻璃化", "step"),
    CRYSTALLIZATION("结晶", "exotherm"),
    MELTING("熔融", "endotherm");

    private final String label;
    private final String shape;

    TransitionType(String label, String shape) {
        this.label = label;
        this.shape = shape;
    }

    public String label() {
        return label;
    }

    public String shape() {
        return shape;
    }
}
