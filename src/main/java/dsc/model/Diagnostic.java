package dsc.model;

/** A machine-readable diagnostic attached to a derivation or source. */
public record Diagnostic(String code, String message, String subject) {}
