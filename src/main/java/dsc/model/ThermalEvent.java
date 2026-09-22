package dsc.model;

public record ThermalEvent(
        String thermalId,
        String intervalId,
        String segmentId,
        String type,
        double onsetK,
        double peakK,
        double endsetK,
        double cpStepJPerGK,
        String direction,
        String baselineCandidateId,
        String producedAtEvent
) {
    public static final String GLASS_TRANSITION = "glass-transition";
    public static final String CRYSTALLIZATION = "crystallization";
    public static final String FUSION = "fusion";
    public static final String ENDO = "endothermic";
    public static final String EXO = "exothermic";
}
