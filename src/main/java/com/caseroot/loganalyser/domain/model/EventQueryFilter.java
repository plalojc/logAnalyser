package com.caseroot.loganalyser.domain.model;

public record EventQueryFilter(
        String level,
        ParseStatus parseStatus,
        String loggerContains,
        String exceptionClass,
        String sourceFile,
        String contains,
        int limit
) {
    public EventQueryFilter {
        limit = limit <= 0 ? 100 : Math.min(limit, 1000);
    }
}
