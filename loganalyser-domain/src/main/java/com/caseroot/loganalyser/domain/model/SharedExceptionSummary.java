package com.caseroot.loganalyser.domain.model;

public record SharedExceptionSummary(
        String exceptionClass,
        String rootCauseClass,
        int jobCount,
        long totalCount
) {
}
