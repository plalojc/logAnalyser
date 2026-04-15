package com.caseroot.loganalyser.caseroot.export;

import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.CaseRootBundle;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.spi.CaseRootExportBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DefaultCaseRootExportBuilder implements CaseRootExportBuilder {

    private final ObjectMapper objectMapper;

    public DefaultCaseRootExportBuilder() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String builderId() {
        return "default-caseroot-export-builder";
    }

    @Override
    public CaseRootBundle buildSkeletonBundle(
            UUID jobId,
            String application,
            String environment,
            RuntimeDescriptor runtimeDescriptor,
            AnalysisSummary summary,
            ArtifactDescriptor bundleArtifact,
            RetentionPolicy retentionPolicy
    ) {
        CaseRootBundle bundle = new CaseRootBundle(
                jobId,
                bundleArtifact.location(),
                List.of(
                        "signature_hash",
                        "runtime.family",
                        "runtime.profile",
                        "logger",
                        "exception_class",
                        "sample_event_refs"
                ),
                List.of(
                        "top_incidents",
                        "timeline_anomalies",
                        "coverage_summary",
                        "artifact_references"
                ),
                bundleArtifact.expiresAt(),
                summary
        );

        writeBundle(bundle, application, environment, runtimeDescriptor, bundleArtifact);
        return bundle;
    }

    private void writeBundle(
            CaseRootBundle bundle,
            String application,
            String environment,
            RuntimeDescriptor runtimeDescriptor,
            ArtifactDescriptor artifactDescriptor
    ) {
        Path path = Path.of(artifactDescriptor.location());

        try {
            Files.createDirectories(path.getParent());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jobId", bundle.jobId());
            payload.put("application", application);
            payload.put("environment", environment);
            payload.put("runtime", runtimeDescriptor);
            payload.put("summary", bundle.summary());
            payload.put("evidenceKeys", bundle.evidenceKeys());
            payload.put("rankedSections", bundle.rankedSections());
            payload.put("expiresAt", bundle.expiresAt());

            Map<String, Object> artifacts = new LinkedHashMap<>();
            artifacts.put("bundle", artifactDescriptor.location());
            payload.put("artifacts", artifacts);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), payload);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write CaseRoot bundle to " + path, exception);
        }
    }
}
