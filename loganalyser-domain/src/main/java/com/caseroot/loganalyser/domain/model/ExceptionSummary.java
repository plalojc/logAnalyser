package com.caseroot.loganalyser.domain.model;

public record ExceptionSummary(
        String exceptionClass,
        String rootCauseClass,
        long count
) {
}

