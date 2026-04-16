package com.caseroot.loganalyser.app.web;

import java.util.List;
import java.util.UUID;

public record CompareJobsRequest(
        List<UUID> jobIds
) {
    public CompareJobsRequest {
        jobIds = jobIds == null ? List.of() : List.copyOf(jobIds);
    }
}
