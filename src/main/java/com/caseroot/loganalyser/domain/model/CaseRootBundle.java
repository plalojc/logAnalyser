package com.caseroot.loganalyser.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CaseRootBundle(
        UUID jobId,
        String location,
        List<String> evidenceKeys,
        List<String> rankedSections,
        Instant expiresAt,
        AnalysisSummary summary
) {
    public CaseRootBundle {
        evidenceKeys = List.copyOf(evidenceKeys);
        rankedSections = List.copyOf(rankedSections);
    }
}
