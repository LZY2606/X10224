package org.dscreview.model;

/** A provenance edge: this derived object depends on id@contentHash playing role. */
public record InputRef(String id, String hash, String role) {}
