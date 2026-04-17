package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GapHighlight(
        long gapMs,
        long occurrenceCount,
        EventSnippet previousEvent,
        EventSnippet currentEvent
) {
}
