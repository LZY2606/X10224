package io.dsc.model;

import java.util.List;

/** Header of a sample-history case. */
public record CaseMetadata(
        String caseId,
        String title,
        String material,
        String createdAt,
        String ruleVersion,
        List<String> tags
) { }
