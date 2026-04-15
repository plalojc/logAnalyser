package com.caseroot.loganalyser.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobComparisonResult(
        List<UUID> jobIds,
        List<JobComparisonEntry> jobs,
        List<SharedSignatureSummary> commonSignatures,
        List<SharedExceptionSummary> commonExceptions,
        Map<String, Long> aggregatedLevelCounts
) {
    public JobComparisonResult {
        jobIds = List.copyOf(jobIds);
        jobs = List.copyOf(jobs);
        commonSignatures = List.copyOf(commonSignatures);
        commonExceptions = List.copyOf(commonExceptions);
        aggregatedLevelCounts = Map.copyOf(aggregatedLevelCounts);
    }
}
