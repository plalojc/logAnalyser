package com.caseroot.loganalyser.caseroot.export;

import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.AnalysisSummaryCounts;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapStatistics;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SignatureSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultCaseRootExportBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    void writesBundleWhenApplicationAndEnvironmentAreNull() throws Exception {
        Path bundlePath = tempDir.resolve("caseroot_input.json");
        ArtifactDescriptor bundleArtifact = new ArtifactDescriptor(
                "caseroot-bundle",
                bundlePath.toString(),
                "application/json",
                Instant.parse("2026-05-01T00:00:00Z")
        );

        AnalysisSummary summary = new AnalysisSummary(
                "legacy-java-parser",
                new RuntimeDescriptor("java", "legacy-java-logs", "legacy_java", "1.0.0"),
                new AnalysisSummaryCounts(10, 2, 2, 0, 0, 1, 0),
                Map.of("ERROR", 1L, "WARN", 1L),
                new GapStatistics(1, 10L, 10L, 10.0, 0, 0, Map.of("0-100ms", 1L)),
                List.of(new SignatureSummary("sig-1", "failed order", "ERROR", "com.acme.OrderService", null, null, "2026-04-15 10:00:00,000", "2026-04-15 10:00:00,000", 1L)),
                List.of(new ExceptionSummary("java.lang.IllegalStateException", "java.lang.IllegalStateException", 1L)),
                List.of()
        );

        RetentionPolicy retentionPolicy = new RetentionPolicy(15, 30, 30, 90);

        DefaultCaseRootExportBuilder builder = new DefaultCaseRootExportBuilder();
        builder.buildSkeletonBundle(
                UUID.randomUUID(),
                null,
                null,
                summary.runtime(),
                summary,
                bundleArtifact,
                retentionPolicy
        );

        assertTrue(Files.exists(bundlePath));
        String content = Files.readString(bundlePath);
        assertTrue(content.contains("\"application\" : null"));
        assertTrue(content.contains("\"environment\" : null"));
    }
}
