package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.CreateAnalysisJobCommand;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.caseroot.loganalyser.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "loganalyser.summary-store.enabled=true",
        "loganalyser.summary-store.driver-class-name=org.h2.Driver",
        "loganalyser.summary-store.jdbc-url=jdbc:h2:mem:loganalyser_app_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "loganalyser.summary-store.table-prefix=it_"
})
class AnalysisSummaryStoreIntegrationTest {

    private static Path basePath;

    @Autowired
    private AnalysisApplicationService analysisApplicationService;

    @Autowired
    private DriverManagerDataSource driverManagerDataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws IOException {
        if (basePath == null) {
            basePath = Files.createTempDirectory("loganalyser-summary-it-");
        }
        registry.add("loganalyser.storage.base-path", () -> basePath.toString());
    }

    @Test
    void persistsSummaryAggregatesWhenStoreEnabled() throws Exception {
        Path inputFile = basePath.resolve("db-input.log");
        Files.writeString(inputFile, """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed order 101
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                2026-04-13 10:15:31,000 ERROR [main] com.acme.OrderService - Failed order 202
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
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
        JdbcTemplate jdbcTemplate = new JdbcTemplate(driverManagerDataSource);

        assertEquals(AnalysisJobStatus.COMPLETED, job.status());
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_job", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_job_summary", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_level_count", Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_gap_bucket", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_signature_summary", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM it_analysis_exception_summary", Integer.class));
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
