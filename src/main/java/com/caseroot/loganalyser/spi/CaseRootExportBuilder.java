package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.CaseRootBundle;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;

import java.util.UUID;

public interface CaseRootExportBuilder {

    String builderId();

    CaseRootBundle buildSkeletonBundle(
            UUID jobId,
            String application,
            String environment,
            RuntimeDescriptor runtimeDescriptor,
            AnalysisSummary summary,
            ArtifactDescriptor bundleArtifact,
            RetentionPolicy retentionPolicy
    );
}
