package org.materials.dsc.domain;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 内置基线模型注册表。 */
public final class BaselineModels {

    private static final List<BaselineModel> MODELS = List.of(
            new ChordBaseline(),
            new TangentLinearBaseline(),
            new SigmoidStepBaseline());

    private static final Map<String, BaselineModel> BY_KEY =
            MODELS.stream().collect(Collectors.toUnmodifiableMap(BaselineModel::key, Function.identity()));

    private BaselineModels() {
    }

    public static List<BaselineModel> all() {
        return MODELS;
    }

    public static BaselineModel byKey(String key) {
        BaselineModel model = BY_KEY.get(key);
        if (model == null) {
            throw new IllegalArgumentException("unknown baseline model: " + key);
        }
        return model;
    }
}
