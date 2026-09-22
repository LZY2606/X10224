package dsc.model;

import java.util.List;

/** 派生对象的一版。deps 形如 "kind:id@version"，用于依赖图传播。 */
public record DerivedRow(
        String id,
        String kind,        // normalized | baseline | event
        int version,
        String sampleId,
        String payload,     // JSON
        List<String> deps,
        String status,      // valid | stale
        String basis,       // 坐标基准说明
        long createdSeq
) {
    public record Dep(String kind, String id, int version) {
        @Override public String toString() { return kind + ":" + id + "@" + version; }
        public static Dep parse(String s) {
            int colon = s.indexOf(':');
            int at = s.lastIndexOf('@');
            return new Dep(s.substring(0, colon), s.substring(colon + 1, at), Integer.parseInt(s.substring(at + 1)));
        }
    }
}
