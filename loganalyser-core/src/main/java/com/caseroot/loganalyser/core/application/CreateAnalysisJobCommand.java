package com.caseroot.loganalyser.core.application;

import com.caseroot.loganalyser.domain.model.SourceType;

public record CreateAnalysisJobCommand(
        SourceType sourceType,
        String sourceLocation,
        String originalFileName,
        String application,
        String environment,
        String requestedParserProfile
) {
}

