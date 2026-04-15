package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;

public interface ParquetArtifactExporter {

    String exporterId();

    boolean enabled();

    void export(ArtifactDescriptor parsedArtifact, ArtifactDescriptor parquetArtifact);
}
