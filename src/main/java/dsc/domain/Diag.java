package dsc.domain;

public record Diag(Severity severity, String code, String message, String relatedEventId, String relatedObjectId) {

    public static Diag error(String code, String message) {
        return new Diag(Severity.ERROR, code, message, null, null);
    }

    public static Diag warning(String code, String message) {
        return new Diag(Severity.WARNING, code, message, null, null);
    }

    public Diag related(String eventId, String objectId) {
        return new Diag(severity, code, message, eventId, objectId);
    }
}
