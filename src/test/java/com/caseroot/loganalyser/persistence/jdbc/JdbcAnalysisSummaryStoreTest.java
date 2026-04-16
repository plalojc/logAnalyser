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

class JdbcAnalysisSummaryStoreTest {

    @Test
    void persistsPhaseTwoSummaryAggregates() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:loganalyser_summary_store;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcAnalysisSummaryStore store = new JdbcAnalysisSummaryStore(jdbcTemplate, "la_", true);

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
                AnalysisJobStatus.COMPLETED,
                Instant.parse("2026-04-13T04:00:00Z"),
                Instant.parse("2026-04-13T04:00:03Z"),
                new RetentionPolicy(15, 30, 30, 90),
                Map.of(),
                null,
                new AnalysisSummary(
                        "legacy-java-parser",
                        new RuntimeDescriptor("java", "legacy-java-logs", "legacy_java", "1.0.0"),
                        new AnalysisSummaryCounts(4, 2, 2, 0, 0, 1, 0),
                        Map.of("ERROR", 1L, "INFO", 1L),
                        new GapStatistics(1, 877L, 877L, 877.0, 0, 0, Map.of("100ms-1s", 1L)),
                        List.of(new SignatureSummary(
                                "abc12345",
                                "Failed order <NUM>",
                                "ERROR",
                                "com.acme.OrderService",
                                "java.lang.IllegalStateException",
                                "java.sql.SQLException",
                                "2026-04-13 10:15:30,123",
                                "2026-04-13 10:15:30,123",
                                1
                        )),
                        List.of(new ExceptionSummary(
                                "java.lang.IllegalStateException",
                                "java.sql.SQLException",
                                1
                        )),
                        List.of()
                ),
                null
        );

        store.persist(job);

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM la_analysis_job_summary", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM la_analysis_level_count", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM la_analysis_gap_bucket", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM la_analysis_signature_summary", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM la_analysis_exception_summary", Integer.class));
    }
}
