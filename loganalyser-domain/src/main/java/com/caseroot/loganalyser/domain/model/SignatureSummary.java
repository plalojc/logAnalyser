package com.caseroot.loganalyser.domain.model;

public record SignatureSummary(
        String signatureHash,
        String normalizedMessage,
        String level,
        String logger,
        String exceptionClass,
        String rootCauseClass,
        String firstSeenTimestamp,
        String lastSeenTimestamp,
        long count
) {
}

