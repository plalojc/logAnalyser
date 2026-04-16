package com.caseroot.loganalyser.export.parquet;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.spi.ParquetArtifactExporter;

public final class NoOpParquetArtifactExporter implements ParquetArtifactExporter {

    @Override
    public String exporterId() {
        return "noop-parquet-exporter";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public void export(ArtifactDescriptor parsedArtifact, ArtifactDescriptor parquetArtifact) {
        // Intentionally disabled.
    }
}
