package com.caseroot.loganalyser.caseroot.export;

import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.AnalysisSummaryCounts;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.EventSnippet;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapHighlight;
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
import java.util.regex.Pattern;

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
                new AnalysisSummaryCounts(10, 2, 2, 2, 0, 0, 1, 0),
                Map.of("ERROR", 1L, "WARN", 1L),
                new GapStatistics(1, 10L, 10L, 10.0, 0, 0, Map.of("0-100ms", 1L)),
                List.of(new SignatureSummary(
                        "sig-1",
                        "com.acme",
                        "com.acme.OrderService",
                        "2026-04-15 10:00:00,000",
                        "2026-04-15 10:00:00,000",
                        1L,
                        Map.of("ERROR", 1L),
                        0L,
                        0L,
                        List.of("failed order"),
                        List.of(new EventSnippet(
                                "xdb.log",
                                "2026-04-15 10:00:00,000",
                                "ERROR",
                                "com.acme.OrderService",
                                "failed order",
                                "2026-04-15 10:00:00,000 ERROR [main] com.acme.OrderService - failed order",
                                "java.lang.IllegalStateException",
                                null,
                                "java.lang.IllegalStateException at com.acme.OrderService.place(OrderService.java:42) [12 frames]"
                        )),
                        List.of(new GapHighlight(
                                180000L,
                                2L,
                                new EventSnippet(
                                        "xdb.log",
                                        "2026-04-15 09:57:00,000",
                                        "INFO",
                                        "com.acme.OrderService",
                                        "started recovery",
                                        "2026-04-15 09:57:00,000 INFO [main] com.acme.OrderService - started recovery",
                                        null,
                                        null,
                                        null
                                ),
                                new EventSnippet(
                                        "xdb.log",
                                        "2026-04-15 10:00:00,000",
                                        "ERROR",
                                        "com.acme.OrderService",
                                        "failed order",
                                        "2026-04-15 10:00:00,000 ERROR [main] com.acme.OrderService - failed order",
                                        "java.lang.IllegalStateException",
                                        null,
                                        "java.lang.IllegalStateException at com.acme.OrderService.place(OrderService.java:42) [12 frames]"
                                )
                        )),
                        List.of()
                )),
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
        assertTrue(content.contains("\"primarySourceFile\" : \"xdb.log\""));
        assertTrue(content.contains("\"occurrenceCount\" : 2"));
        assertTrue(!content.contains("\"sourceFile\" : \"xdb.log\""));
        assertTrue(!content.contains("\"rootCauseClass\" : null"));
        assertTrue(!content.contains("\"message\" : \"failed order\""));
        assertTrue(!content.contains("\"logger\" : \"com.acme.OrderService\""));
        assertTrue(Pattern.compile("\"exceptionClass\"\\s*:\\s*\"java\\.lang\\.IllegalStateException\"")
                .matcher(content)
                .results()
                .count() == 1);
        assertTrue(content.contains("\"statement\" : \"2026-04-15 10:00:00,000 ERROR [main] com.acme.OrderService - failed order\""));
        assertTrue(content.contains("\"stackSummary\" : \"java.lang.IllegalStateException at com.acme.OrderService.place(OrderService.java:42) [12 frames]\""));
    }
}
