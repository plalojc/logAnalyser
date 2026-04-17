package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.CreateAnalysisJobCommand;
import com.caseroot.loganalyser.domain.model.AnalysisOptions;
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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "loganalyser.output.parquet-enabled=true")
class ComparisonAndParquetIntegrationTest {

    private static Path basePath;

    @Autowired
    private AnalysisApplicationService analysisApplicationService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (basePath == null) {
            basePath = Files.createTempDirectory("loganalyser-phase4-it-");
        }
        registry.add("loganalyser.storage.base-path", () -> basePath.toString());
    }

    @Test
    void exportsParquetAndBuildsComparisonDashboardData() throws Exception {
        Path inputOne = basePath.resolve("compare-one.log");
        Path inputTwo = basePath.resolve("compare-two.log");
        Files.writeString(inputOne, """
                2026-04-15 10:15:30,123 ERROR [main] com.acme.OrderService - Failed order 101 for request 11111111-1111-1111-1111-111111111111
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                """);
        Files.writeString(inputTwo, """
                2026-04-15 10:16:30,123 ERROR [main] com.acme.OrderService - Failed order 202 for request 22222222-2222-2222-2222-222222222222
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                """);

        var jobOne = awaitCompletion(analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                SourceType.FILE_PATH,
                inputOne.toString(),
                inputOne.getFileName().toString(),
                "order-service",
                "prod",
                "log4j_pattern",
                new AnalysisOptions(null, List.of())
        )).jobId(), Duration.ofSeconds(8));

        var jobTwo = awaitCompletion(analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                SourceType.FILE_PATH,
                inputTwo.toString(),
                inputTwo.getFileName().toString(),
                "order-service",
                "prod",
                "log4j_pattern",
                new AnalysisOptions(null, List.of())
        )).jobId(), Duration.ofSeconds(8));

        Path parquetPath = Path.of(jobOne.artifacts().get("parquet-events").location());
        byte[] bytes = Files.readAllBytes(parquetPath);

        var comparison = analysisApplicationService.compareJobs(List.of(jobOne.jobId(), jobTwo.jobId()));
        var dashboard = analysisApplicationService.dashboardOverview();

        assertEquals(AnalysisJobStatus.COMPLETED, jobOne.status());
        assertEquals(AnalysisJobStatus.COMPLETED, jobTwo.status());
        assertTrue(jobOne.artifacts().containsKey("parquet-events"));
        assertTrue(Files.exists(parquetPath));
        assertTrue(bytes.length > 8);
        assertArrayEquals(new byte[]{'P', 'A', 'R', '1'}, Arrays.copyOfRange(bytes, 0, 4));
        assertTrue(comparison.commonSignatures().stream().anyMatch(item -> item.jobCount() == 2));
        assertTrue(dashboard.totalJobs() >= 2);
        assertTrue(dashboard.completedJobs() >= 2);
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
