package com.caseroot.loganalyser.domain.model;

public record SharedSignatureSummary(
        String signatureHash,
        String packageName,
        String representativeMessage,
        int jobCount,
        long totalCount
) {
}
