package com.caseroot.loganalyser.persistence.jdbc;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.AnalysisSummaryCounts;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapStatistics;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SignatureSummary;
import com.caseroot.loganalyser.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcJobRepositoryTest {

    @Test
    void roundTripsAnalysisJob() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:loganalyser_job_repo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");

        JdbcJobRepository repository = new JdbcJobRepository(new JdbcTemplate(dataSource), "la_", true);
        UUID jobId = UUID.randomUUID();

        AnalysisJob job = new AnalysisJob(
                jobId,
                SourceType.FILE_PATH,
                "C:\\logs\\sample.log",
                "sample.log",
                "order-service",
                "prod",
                "log4j_pattern",
                "legacy-java-parser",
                new RuntimeDescriptor("java", "legacy-java-logs", "legacy_java", "1.0.0"),
                AnalysisJobStatus.RUNNING,
                Instant.parse("2026-04-13T04:00:00Z"),
                Instant.parse("2026-04-13T04:00:01Z"),
                new RetentionPolicy(15, 30, 30, 90),
                Map.of(),
                null,
                new AnalysisSummary(
                        "legacy-java-parser",
                        new RuntimeDescriptor("java", "legacy-java-logs", "legacy_java", "1.0.0"),
                        new AnalysisSummaryCounts(4, 2, 2, 0, 0, 1, 0),
                        Map.of("ERROR", 2L),
                        new GapStatistics(1, 877L, 877L, 877.0, 0, 0, Map.of("100ms-1s", 1L)),
                        List.of(new SignatureSummary(
                                "abc12345",
                                "Failed order <NUM>",
                                "ERROR",
                                "com.acme.OrderService",
                                "java.lang.IllegalStateException",
                                null,
                                "2026-04-13 10:15:30,123",
                                "2026-04-13 10:15:31,000",
                                2
                        )),
                        List.of(new ExceptionSummary("java.lang.IllegalStateException", null, 2)),
                        List.of("warning")
                ),
                null
        );

        repository.save(job);

        var loaded = repository.findById(jobId);

        assertTrue(loaded.isPresent());
        assertEquals(AnalysisJobStatus.RUNNING, loaded.get().status());
        assertEquals("legacy-java-parser", loaded.get().summary().parserPluginId());
        assertEquals(2L, loaded.get().summary().topSignatures().getFirst().count());
    }
}
