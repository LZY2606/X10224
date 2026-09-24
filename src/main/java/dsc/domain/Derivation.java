package dsc.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一次完整派生的产物集合与节点指纹。 */
public final class Derivation {
    public final Map<String, NormalizedCurve> curves = new LinkedHashMap<>();
    public final Map<String, TangentSelection> tangents = new LinkedHashMap<>();
    public final Map<String, WindowResult> windows = new LinkedHashMap<>();
    public final Map<String, Fingerprint> fingerprints = new LinkedHashMap<>();

    /** 每个节点仍有效的原因 / 需要重算的原因，供依赖图说明。 */
    public final Map<String, String> validity = new LinkedHashMap<>();

    /** 本次派生识别到的异常（反向热流、非等间隔、重叠、空区间等）。 */
    public final List<Anomaly> anomalies = new ArrayList<>();

    public WindowResult window(String id) { return windows.get(id); }
}
