package com.caseroot.loganalyser.domain.model;

import java.util.List;
import java.util.Map;

public record DashboardOverview(
        long totalJobs,
        long acceptedJobs,
        long runningJobs,
        long completedJobs,
        long failedJobs,
        long totalEventsAcrossCompletedJobs,
        Map<String, Long> jobsByApplication,
        Map<String, Long> jobsByRuntimeFamily,
        List<JobComparisonEntry> recentJobs
) {
    public DashboardOverview {
        jobsByApplication = Map.copyOf(jobsByApplication);
        jobsByRuntimeFamily = Map.copyOf(jobsByRuntimeFamily);
        recentJobs = List.copyOf(recentJobs);
    }
}
