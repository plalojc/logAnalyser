package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.CreateAnalysisJobCommand;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.caseroot.loganalyser.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AnalysisJobControllerIntegrationTest {

    private static Path basePath;

    @Autowired
    private AnalysisApplicationService analysisApplicationService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (basePath == null) {
            basePath = Files.createTempDirectory("loganalyser-it-");
        }
        registry.add("loganalyser.storage.base-path", () -> basePath.toString());
    }

    @Test
    void createsJobAndBuildsPhaseTwoArtifacts() throws Exception {
        Path inputFile = basePath.resolve("input.log");
        Files.writeString(inputFile, """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed to place order
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                2026-04-13 10:15:31,000 INFO [main] com.acme.OrderService - Recovered
                """);

        var submittedJob = analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                SourceType.FILE_PATH,
                inputFile.toString(),
                inputFile.getFileName().toString(),
                "order-service",
                "prod",
                "log4j_pattern"
        ));

        assertTrue(submittedJob.status() == AnalysisJobStatus.ACCEPTED || submittedJob.status() == AnalysisJobStatus.RUNNING);

        var job = awaitCompletion(submittedJob.jobId(), Duration.ofSeconds(5));

        assertEquals(AnalysisJobStatus.COMPLETED, job.status());
        assertEquals("legacy-java-parser", job.selectedParserPlugin());
        assertNotNull(job.summary());
        assertEquals(2L, job.summary().counts().totalEvents());
        assertEquals(1L, job.summary().counts().multilineEvents());
        assertEquals(1L, job.summary().gapStatistics().totalGaps());
        assertEquals(877L, job.summary().gapStatistics().maxGapMs());
        assertEquals(2, job.summary().topSignatures().size());
        assertEquals(1, job.summary().topExceptions().size());
        assertTrue(Files.exists(Path.of(job.artifacts().get("summary").location())));
        assertTrue(Files.exists(Path.of(job.artifacts().get("parsed-events").location())));
        assertTrue(Files.exists(Path.of(job.artifacts().get("caseroot-bundle").location())));
    }

    private com.caseroot.loganalyser.domain.model.AnalysisJob awaitCompletion(java.util.UUID jobId, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();

        while (System.nanoTime() < deadline) {
            var job = analysisApplicationService.getJob(jobId).orElseThrow();
            if (job.status() == AnalysisJobStatus.COMPLETED || job.status() == AnalysisJobStatus.FAILED) {
                return job;
            }
            Thread.sleep(50);
        }

        throw new AssertionError("Timed out waiting for job completion: " + jobId);
    }
}
