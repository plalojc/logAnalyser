package com.caseroot.loganalyser.core.persistence;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.spi.AnalysisSummaryStore;

public final class NoOpAnalysisSummaryStore implements AnalysisSummaryStore {

    @Override
    public String storeId() {
        return "noop-summary-store";
    }

    @Override
    public void persist(AnalysisJob analysisJob) {
        // Intentionally empty when aggregate persistence is disabled.
    }
}
