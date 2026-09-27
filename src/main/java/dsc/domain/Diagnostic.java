package dsc.domain;

/**
 * Severity for retained problems. ERROR values block calculation (the value
 * stays in place but cannot be normalized); WARNING keeps the value usable.
 */
public enum Severity {
    ERROR, WARNING, INFO
}
