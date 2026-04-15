package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;

import java.util.UUID;

public interface ArtifactStorage {

    String storageId();

    ArtifactDescriptor allocateRawLog(UUID jobId, String originalFileName, RetentionPolicy retentionPolicy);

    ArtifactDescriptor allocateParsedEvents(UUID jobId, RetentionPolicy retentionPolicy);

    ArtifactDescriptor allocateParquetEvents(UUID jobId, RetentionPolicy retentionPolicy);

    ArtifactDescriptor allocateSummary(UUID jobId, RetentionPolicy retentionPolicy);

    ArtifactDescriptor allocateCaseRootBundle(UUID jobId, RetentionPolicy retentionPolicy);
}
