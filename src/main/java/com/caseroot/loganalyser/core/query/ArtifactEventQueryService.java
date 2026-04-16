package com.caseroot.loganalyser.core.query;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.EventQueryResult;

import java.util.UUID;

public interface ArtifactEventQueryService {

    EventQueryResult query(UUID jobId, ArtifactDescriptor parsedArtifact, EventQueryFilter filter);
}
