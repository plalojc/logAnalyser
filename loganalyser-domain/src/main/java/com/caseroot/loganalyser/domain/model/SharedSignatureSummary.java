package com.caseroot.loganalyser.domain.model;

public record SharedSignatureSummary(
        String signatureHash,
        String normalizedMessage,
        int jobCount,
        long totalCount
) {
}
