package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.AnalysisJob;

public interface AnalysisSummaryStore {

    String storeId();

    void persist(AnalysisJob analysisJob);
}
