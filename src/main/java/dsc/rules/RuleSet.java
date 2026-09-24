package dsc.rules;

/**
 * 规则版本。参数修订产生新版本（v1 -> v2），旧版本产物保留，
 * 使任何历史事件都能用当时的规则版本重算。
 */
public record RuleSet(String version,
                      double onsetTangentWindow,
                      double overlapSeparationK,
                      double sigmoidStepWindow,
                      boolean allowCrossSegment,
                      String canonicalSign) {

    public static RuleSet v1() {
        return new RuleSet("rules-v1", 3.0, 8.0, 2.0, false, "ENDO_UP");
    }

    /** 一次参数修订：重叠转折分离阈值 8K -> 6K，签名基准维持 ENDO_UP。 */
    public static RuleSet v2() {
        return new RuleSet("rules-v2", 3.0, 6.0, 2.0, false, "ENDO_UP");
    }

    public static RuleSet of(String version) {
        return switch (version) {
            case "rules-v1" -> v1();
            case "rules-v2" -> v2();
            default -> throw new IllegalArgumentException("未知规则版本: " + version);
        };
    }
}
