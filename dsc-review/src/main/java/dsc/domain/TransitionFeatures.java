package dsc.domain;

import java.util.List;

/**
 * 相变转折特征。温度均为标定后 K；热流为吸热为正的 mW。
 * kind 为玻璃化/结晶/熔融等分类。
 */
public record TransitionFeatures(String transitionId,
                                 String segmentId,
                                 String kind,
                                 double onsetK,
                                 double peakK,
                                 double endsetK,
                                 double peakHeatMilliWatt,
                                 double deltaHJoule,
                                 double deltaHPerMassJoulePerGram,
                                 double uncertaintyJoule,
                                 String baselineCandidateId,
                                 List<String> errorSources,
                                 double windowStartK,
                                 double windowEndK) {
}
