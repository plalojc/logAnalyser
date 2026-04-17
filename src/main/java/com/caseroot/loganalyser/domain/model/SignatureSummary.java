package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignatureSummary(
        String signatureHash,
        String packageName,
        String representativeLogger,
        String firstSeenTimestamp,
        String lastSeenTimestamp,
        long count,
        Map<String, Long> levelCounts,
        long exceptionCount,
        long largeGapCount,
        List<String> sampleMessages,
        List<EventSnippet> sampleEvents,
        List<GapHighlight> gapHighlights,
        List<String> highlights
) {
    public SignatureSummary {
        levelCounts = Map.copyOf(levelCounts);
        sampleMessages = List.copyOf(sampleMessages);
        sampleEvents = List.copyOf(sampleEvents);
        gapHighlights = List.copyOf(gapHighlights);
        highlights = List.copyOf(highlights);
    }
}
