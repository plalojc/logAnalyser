package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.domain.model.SourceType;

public record CreateJobRequest(
        SourceType sourceType,
        String sourceLocation,
        String originalFileName,
        String application,
        String environment,
        String requestedParserProfile
) {
}

