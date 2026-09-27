package dsc.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次完整推导的结果版本。只有完整成功后 current 指针才切到它；
 * 写入中断重启后只能看到旧版本或完整新版本。
 */
public final class ResultVersion {
    public String versionId;
    public int basedOnEventSeq;
    public int sourceRevision;
    public String createdAt;
    public boolean current;
    public String contentHash;
    public Map<String, String> nodeHashes = new LinkedHashMap<>();
    public Map<String, String> nodeStatus = new LinkedHashMap<>();
    public List<NormalizedCurve> curves = new ArrayList<>();
    public List<Finding> findings = new ArrayList<>();
    public List<DeriveDiagnostic> diagnostics = new ArrayList<>();

    public ResultVersion() {}

    public NormalizedCurve curve(String seg) {
        for (NormalizedCurve c : curves) if (c.segmentId.equals(seg)) return c;
        return null;
    }

    public List<Finding> findingsFor(String windowId) {
        List<Finding> out = new ArrayList<>();
        for (Finding f : findings) if (f.windowId.equals(windowId)) out.add(f);
        return out;
    }
}
