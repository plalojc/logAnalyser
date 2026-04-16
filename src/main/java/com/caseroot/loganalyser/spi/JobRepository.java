package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.AnalysisJob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRepository {

    AnalysisJob save(AnalysisJob analysisJob);

    Optional<AnalysisJob> findById(UUID jobId);

    List<AnalysisJob> findAll();
}

