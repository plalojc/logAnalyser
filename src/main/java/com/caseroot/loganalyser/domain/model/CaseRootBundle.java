package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CaseRootBundle(
        UUID jobId,
        String location,
        String primarySourceFile,
        List<String> sourceFiles,
        List<String> evidenceKeys,
        List<String> rankedSections,
        Instant expiresAt,
        AnalysisSummary summary
) {
    public CaseRootBundle {
        sourceFiles = sourceFiles == null ? null : List.copyOf(sourceFiles);
        evidenceKeys = List.copyOf(evidenceKeys);
        rankedSections = List.copyOf(rankedSections);
    }
}
