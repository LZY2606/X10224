package dsc.model;

public record Diagnostic(
        String diagId,
        String code,
        String severity,
        String scope,
        String targetId,
        String message,
        String evidence,
        long firstSeenSeq,
        long lastSeenSeq,
        String resolvedAtEvent
) {
    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String INFO = "info";
}
