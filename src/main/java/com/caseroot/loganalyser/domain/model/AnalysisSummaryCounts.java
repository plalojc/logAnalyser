package com.caseroot.loganalyser.domain.model;

public record AnalysisSummaryCounts(
        long totalInputLines,
        long totalEvents,
        long focusedEvents,
        long parsedEvents,
        long partialEvents,
        long unclassifiedEvents,
        long multilineEvents,
        long droppedEvents
) {
}
