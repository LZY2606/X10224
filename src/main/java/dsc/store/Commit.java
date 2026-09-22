package dsc.store;

import dsc.domain.Derivation;
import dsc.domain.Event;
import dsc.domain.SourceDoc;

import java.util.List;

/**
 * 一次原子提交：事件 + 新增来源版本 + 新增派生版本 + 指针移动 + 快照。
 * 全部在单个事务内写入；提交中断时重启只能看到旧版本或完整新版本。
 */
public record Commit(
        Event event,
        List<SourceDoc> newSourceVersions,
        List<Derivation> newDerivations,
        List<PointerMove> pointerMoves,
        String snapshotJson,
        String snapshotLabel
) {

    public record PointerMove(String scope, String ref, String key, int currentVersion) {
    }
}
