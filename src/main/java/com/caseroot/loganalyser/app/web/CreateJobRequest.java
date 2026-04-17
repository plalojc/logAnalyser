package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.domain.model.SourceType;

import java.util.List;

public record CreateJobRequest(
        SourceType sourceType,
        String sourceLocation,
        String originalFileName,
        String application,
        String environment,
        String requestedParserProfile,
        Long largeGapHighlightThresholdMinutes,
        List<String> analysisFocus
) {
}
