package dsc.model;

/**
 * A machine-readable judgement attached to a derivation or an event.
 * Diagnostics are how the system refuses to swallow ambiguity:
 * unconvertible values, ambiguous direction, empty intervals etc. stay
 * in place with a diagnostic instead of being dropped.
 */
public record Diagnostic(String code, String message, String location) {}
