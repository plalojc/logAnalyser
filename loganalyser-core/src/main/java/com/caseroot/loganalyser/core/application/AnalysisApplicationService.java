package com.caseroot.loganalyser.core.application;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.DashboardOverview;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.EventQueryResult;
import com.caseroot.loganalyser.domain.model.JobComparisonResult;
import com.caseroot.loganalyser.domain.model.ModuleDescriptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisApplicationService {

    AnalysisJob createJob(CreateAnalysisJobCommand command);

    Optional<AnalysisJob> getJob(UUID jobId);

    List<AnalysisJob> listJobs();

    EventQueryResult queryEvents(UUID jobId, EventQueryFilter filter);

    JobComparisonResult compareJobs(List<UUID> jobIds);

    DashboardOverview dashboardOverview();

    List<ModuleDescriptor> listModules();
}
