package dsc.model;

/**
 * A sample history ("sample履历"): one material specimen followed through
 * several heating / cooling legs of a thermal cycle.
 */
public record Study(
        String id,
        String materialCode,
        String description,
        String createdAt,
        long headSeq,
        /** Optimistic-concurrency token: the seq this working copy started from. */
        long baseSeq
) {
    public Study withHead(long newHead) {
        return new Study(id, materialCode, description, createdAt, newHead, baseSeq);
    }

    public Study withBase(long newBase) {
        return new Study(id, materialCode, description, createdAt, headSeq, newBase);
    }
}
