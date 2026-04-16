package com.caseroot.loganalyser.domain.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AnalysisJob(
        UUID jobId,
        SourceType sourceType,
        String sourceLocation,
        String originalFileName,
        String application,
        String environment,
        String requestedParserProfile,
        String selectedParserPlugin,
        RuntimeDescriptor runtimeDescriptor,
        AnalysisJobStatus status,
        Instant createdAt,
        Instant updatedAt,
        RetentionPolicy retentionPolicy,
        Map<String, ArtifactDescriptor> artifacts,
        CaseRootBundle caseRootBundle,
        AnalysisSummary summary,
        String failureReason
) {
    public AnalysisJob {
        artifacts = Map.copyOf(artifacts);
    }
}
