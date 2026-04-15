package com.caseroot.loganalyser.domain.model;

public record AnalysisSummaryCounts(
        long totalInputLines,
        long totalEvents,
        long parsedEvents,
        long partialEvents,
        long unclassifiedEvents,
        long multilineEvents,
        long droppedEvents
) {
}

