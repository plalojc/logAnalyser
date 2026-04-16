package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.CreateAnalysisJobCommand;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class DirectoryBatchAndEventQueryIntegrationTest {

    private static Path basePath;

    @Autowired
    private AnalysisApplicationService analysisApplicationService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (basePath == null) {
            basePath = Files.createTempDirectory("loganalyser-phase3-it-");
        }
        registry.add("loganalyser.storage.base-path", () -> basePath.toString());
    }

    @Test
    void ingestsDirectoryAndQueriesEventsBySourceFile() throws Exception {
        Path directory = basePath.resolve("batch");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("app-1.log"), """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed order 101
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                """);
        Files.writeString(directory.resolve("app-2.log"), """
                2026-04-13 10:15:31,000 INFO [main] com.acme.OrderService - Recovered order 101
                """);

        var submittedJob = analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                SourceType.DIRECTORY,
                directory.toString(),
                "batch",
                "order-service",
                "prod",
                "log4j_pattern"
        ));

        var completedJob = awaitCompletion(submittedJob.jobId(), Duration.ofSeconds(5));
        var result = analysisApplicationService.queryEvents(completedJob.jobId(), new EventQueryFilter(
                "ERROR",
                null,
                null,
                null,
                "app-1.log",
                "Failed order",
                20
        ));

        assertEquals(AnalysisJobStatus.COMPLETED, completedJob.status());
        assertEquals(2L, completedJob.summary().counts().totalEvents());
        assertEquals(1, result.returnedEvents());
        assertEquals("app-1.log", result.events().getFirst().sourceFile());
        assertFalse(result.truncated());
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
