package com.caseroot.loganalyser.persistence.jdbc;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapStatistics;
import com.caseroot.loganalyser.domain.model.SignatureSummary;
import com.caseroot.loganalyser.spi.AnalysisSummaryStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class JdbcAnalysisSummaryStore implements AnalysisSummaryStore {

    private final JdbcTemplate jdbcTemplate;
    private final String tablePrefix;
    private final boolean initializeSchema;
    private volatile boolean schemaReady;

    public JdbcAnalysisSummaryStore(JdbcTemplate jdbcTemplate, String tablePrefix, boolean initializeSchema) {
        this.jdbcTemplate = jdbcTemplate;
        this.tablePrefix = sanitizePrefix(tablePrefix);
        this.initializeSchema = initializeSchema;
    }

    @Override
    public String storeId() {
        return "jdbc-summary-store";
    }

    @Override
    public void persist(AnalysisJob analysisJob) {
        if (analysisJob.summary() == null) {
            return;
        }

        ensureSchema();
        UUID jobId = analysisJob.jobId();
        AnalysisSummary summary = analysisJob.summary();

        jdbcTemplate.update("DELETE FROM " + table("analysis_exception_summary") + " WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM " + table("analysis_signature_summary") + " WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM " + table("analysis_gap_bucket") + " WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM " + table("analysis_level_count") + " WHERE job_id = ?", jobId);
        jdbcTemplate.update("DELETE FROM " + table("analysis_job_summary") + " WHERE job_id = ?", jobId);

        insertJobSummary(analysisJob, summary);
        insertLevelCounts(jobId, summary.levelCounts());
        insertGapBuckets(jobId, summary.gapStatistics());
        insertSignatureSummaries(jobId, summary.topSignatures());
        insertExceptionSummaries(jobId, summary.topExceptions());
    }

    private void insertJobSummary(AnalysisJob analysisJob, AnalysisSummary summary) {
        GapStatistics gapStatistics = summary.gapStatistics();
        jdbcTemplate.update(
                "INSERT INTO " + table("analysis_job_summary") + " ("
                        + "job_id, application, environment, parser_plugin_id, runtime_family, runtime_framework, runtime_profile, runtime_version, "
                        + "status, source_type, total_input_lines, total_events, parsed_events, partial_events, unclassified_events, multiline_events, dropped_events, "
                        + "total_gaps, min_gap_ms, max_gap_ms, avg_gap_ms, out_of_order_gaps, missing_timestamp_events, warning_count, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                analysisJob.jobId(),
                analysisJob.application(),
                analysisJob.environment(),
                summary.parserPluginId(),
                summary.runtime().family(),
                summary.runtime().framework(),
                summary.runtime().profile(),
                summary.runtime().profileVersion(),
                analysisJob.status().name(),
                analysisJob.sourceType().name(),
                summary.counts().totalInputLines(),
                summary.counts().totalEvents(),
                summary.counts().parsedEvents(),
                summary.counts().partialEvents(),
                summary.counts().unclassifiedEvents(),
                summary.counts().multilineEvents(),
                summary.counts().droppedEvents(),
                gapStatistics.totalGaps(),
                gapStatistics.minGapMs(),
                gapStatistics.maxGapMs(),
                gapStatistics.avgGapMs(),
                gapStatistics.outOfOrderGaps(),
                gapStatistics.missingTimestampEvents(),
                summary.warnings().size(),
                analysisJob.createdAt(),
                analysisJob.updatedAt()
        );
    }

    private void insertLevelCounts(UUID jobId, Map<String, Long> levelCounts) {
        for (Map.Entry<String, Long> entry : levelCounts.entrySet()) {
            jdbcTemplate.update(
                    "INSERT INTO " + table("analysis_level_count") + " (job_id, level_name, event_count) VALUES (?, ?, ?)",
                    jobId,
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private void insertGapBuckets(UUID jobId, GapStatistics gapStatistics) {
        for (Map.Entry<String, Long> entry : gapStatistics.buckets().entrySet()) {
            jdbcTemplate.update(
                    "INSERT INTO " + table("analysis_gap_bucket") + " (job_id, bucket_name, event_count) VALUES (?, ?, ?)",
                    jobId,
                    entry.getKey(),
                    entry.getValue()
            );
        }
    }

    private void insertSignatureSummaries(UUID jobId, List<SignatureSummary> signatures) {
        for (int index = 0; index < signatures.size(); index++) {
            SignatureSummary signature = signatures.get(index);
            jdbcTemplate.update(
                    "INSERT INTO " + table("analysis_signature_summary") + " ("
                            + "job_id, rank_index, signature_hash, normalized_message, level_name, logger_name, exception_class, root_cause_class, "
                            + "first_seen_timestamp, last_seen_timestamp, event_count"
                            + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    jobId,
                    index + 1,
                    signature.signatureHash(),
                    signature.normalizedMessage(),
                    signature.level(),
                    signature.logger(),
                    signature.exceptionClass(),
                    signature.rootCauseClass(),
                    signature.firstSeenTimestamp(),
                    signature.lastSeenTimestamp(),
                    signature.count()
            );
        }
    }

    private void insertExceptionSummaries(UUID jobId, List<ExceptionSummary> exceptions) {
        for (int index = 0; index < exceptions.size(); index++) {
            ExceptionSummary exception = exceptions.get(index);
            jdbcTemplate.update(
                    "INSERT INTO " + table("analysis_exception_summary") + " (job_id, rank_index, exception_class, root_cause_class, event_count) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    jobId,
                    index + 1,
                    exception.exceptionClass(),
                    exception.rootCauseClass(),
                    exception.count()
            );
        }
    }

    private void ensureSchema() {
        if (!initializeSchema || schemaReady) {
            return;
        }

        synchronized (this) {
            if (schemaReady) {
                return;
            }

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        job_id UUID PRIMARY KEY,
                        application VARCHAR(200),
                        environment VARCHAR(120),
                        parser_plugin_id VARCHAR(160) NOT NULL,
                        runtime_family VARCHAR(120) NOT NULL,
                        runtime_framework VARCHAR(120) NOT NULL,
                        runtime_profile VARCHAR(120) NOT NULL,
                        runtime_version VARCHAR(80),
                        status VARCHAR(40) NOT NULL,
                        source_type VARCHAR(40) NOT NULL,
                        total_input_lines BIGINT NOT NULL,
                        total_events BIGINT NOT NULL,
                        parsed_events BIGINT NOT NULL,
                        partial_events BIGINT NOT NULL,
                        unclassified_events BIGINT NOT NULL,
                        multiline_events BIGINT NOT NULL,
                        dropped_events BIGINT NOT NULL,
                        total_gaps BIGINT NOT NULL,
                        min_gap_ms BIGINT,
                        max_gap_ms BIGINT,
                        avg_gap_ms DOUBLE PRECISION,
                        out_of_order_gaps BIGINT NOT NULL,
                        missing_timestamp_events BIGINT NOT NULL,
                        warning_count BIGINT NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL
                    )
                    """.formatted(table("analysis_job_summary")));

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        job_id UUID NOT NULL,
                        level_name VARCHAR(40) NOT NULL,
                        event_count BIGINT NOT NULL,
                        PRIMARY KEY (job_id, level_name)
                    )
                    """.formatted(table("analysis_level_count")));

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        job_id UUID NOT NULL,
                        bucket_name VARCHAR(40) NOT NULL,
                        event_count BIGINT NOT NULL,
                        PRIMARY KEY (job_id, bucket_name)
                    )
                    """.formatted(table("analysis_gap_bucket")));

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        job_id UUID NOT NULL,
                        rank_index INTEGER NOT NULL,
                        signature_hash VARCHAR(64) NOT NULL,
                        normalized_message VARCHAR(2000) NOT NULL,
                        level_name VARCHAR(40),
                        logger_name VARCHAR(255),
                        exception_class VARCHAR(255),
                        root_cause_class VARCHAR(255),
                        first_seen_timestamp VARCHAR(80),
                        last_seen_timestamp VARCHAR(80),
                        event_count BIGINT NOT NULL,
                        PRIMARY KEY (job_id, rank_index)
                    )
                    """.formatted(table("analysis_signature_summary")));

            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        job_id UUID NOT NULL,
                        rank_index INTEGER NOT NULL,
                        exception_class VARCHAR(255),
                        root_cause_class VARCHAR(255),
                        event_count BIGINT NOT NULL,
                        PRIMARY KEY (job_id, rank_index)
                    )
                    """.formatted(table("analysis_exception_summary")));

            schemaReady = true;
        }
    }

    private String table(String baseName) {
        return tablePrefix + baseName;
    }

    private String sanitizePrefix(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "";
        }
        if (!candidate.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Table prefix must contain only letters, numbers, or underscores.");
        }
        return candidate;
    }
}
