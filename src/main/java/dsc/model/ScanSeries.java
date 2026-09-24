package dsc.model;

import java.util.List;

/** A parsed instrument output: identity + segments. Raw text stays in the archive. */
public record ScanSeries(String id, List<Segment> segments) {}
