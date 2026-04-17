package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventSnippet(
        String sourceFile,
        String timestamp,
        String level,
        String logger,
        String message,
        String statement,
        String exceptionClass,
        String rootCauseClass,
        String stackSummary
) {
}
