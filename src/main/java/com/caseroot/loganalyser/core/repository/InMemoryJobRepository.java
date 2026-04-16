package com.caseroot.loganalyser.core.repository;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.spi.JobRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryJobRepository implements JobRepository {

    private final ConcurrentMap<UUID, AnalysisJob> jobs = new ConcurrentHashMap<>();

    @Override
    public AnalysisJob save(AnalysisJob analysisJob) {
        jobs.put(analysisJob.jobId(), analysisJob);
        return analysisJob;
    }

    @Override
    public Optional<AnalysisJob> findById(UUID jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    @Override
    public List<AnalysisJob> findAll() {
        return new ArrayList<>(jobs.values());
    }
}

