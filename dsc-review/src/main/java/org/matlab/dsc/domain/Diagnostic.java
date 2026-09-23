package org.matlab.dsc.domain;

/**
 * A value that could not be normalised is never deleted: it stays at its
 * original position and gets a diagnostic explaining why.
 */
public record Diagnostic(String id, String ownerRef, String position,
                         String code, String rawValue, String detail) {
}
