package com.caseroot.loganalyser.persistence.jdbc;

import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.CaseRootBundle;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SourceType;
import com.caseroot.loganalyser.spi.JobRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.UncheckedIOException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class JdbcJobRepository implements JobRepository {

    private static final TypeReference<Map<String, ArtifactDescriptor>> ARTIFACTS_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String tablePrefix;
    private final boolean initializeSchema;
    private volatile boolean schemaReady;

    public JdbcJobRepository(JdbcTemplate jdbcTemplate, String tablePrefix, boolean initializeSchema) {
        this.jdbcTemplate = jdbcTemplate;
        this.tablePrefix = sanitizePrefix(tablePrefix);
        this.initializeSchema = initializeSchema;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public AnalysisJob save(AnalysisJob analysisJob) {
        ensureSchema();
        jdbcTemplate.update("DELETE FROM " + table("analysis_job") + " WHERE job_id = ?", analysisJob.jobId());
        jdbcTemplate.update(
                "INSERT INTO " + table("analysis_job") + " ("
                        + "job_id, source_type, source_location, original_file_name, application, environment, requested_parser_profile, "
                        + "selected_parser_plugin, runtime_descriptor_json, status, created_at, updated_at, retention_policy_json, artifacts_json, "
                        + "caseroot_bundle_json, summary_json, failure_reason"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                analysisJob.jobId(),
                analysisJob.sourceType().name(),
                analysisJob.sourceLocation(),
                analysisJob.originalFileName(),
                analysisJob.application(),
                analysisJob.environment(),
                analysisJob.requestedParserProfile(),
                analysisJob.selectedParserPlugin(),
                writeJson(analysisJob.runtimeDescriptor()),
                analysisJob.status().name(),
                analysisJob.createdAt(),
                analysisJob.updatedAt(),
                writeJson(analysisJob.retentionPolicy()),
                writeJson(analysisJob.artifacts()),
                writeJson(analysisJob.caseRootBundle()),
                writeJson(analysisJob.summary()),
                analysisJob.failureReason()
        );
        return analysisJob;
    }

    @Override
    public Optional<AnalysisJob> findById(UUID jobId) {
        ensureSchema();
        List<AnalysisJob> jobs = jdbcTemplate.query(
                "SELECT * FROM " + table("analysis_job") + " WHERE job_id = ?",
                this::mapJob,
                jobId
        );
        return jobs.stream().findFirst();
    }

    @Override
    public List<AnalysisJob> findAll() {
        ensureSchema();
        return jdbcTemplate.query(
                "SELECT * FROM " + table("analysis_job") + " ORDER BY created_at DESC",
                this::mapJob
        );
    }

    private AnalysisJob mapJob(ResultSet resultSet, int rowNum) throws SQLException {
        return new AnalysisJob(
                resultSet.getObject("job_id", UUID.class),
                SourceType.valueOf(resultSet.getString("source_type")),
                resultSet.getString("source_location"),
                resultSet.getString("original_file_name"),
                resultSet.getString("application"),
                resultSet.getString("environment"),
                resultSet.getString("requested_parser_profile"),
                resultSet.getString("selected_parser_plugin"),
                readJson(resultSet.getString("runtime_descriptor_json"), RuntimeDescriptor.class),
                com.caseroot.loganalyser.domain.model.AnalysisJobStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                readJson(resultSet.getString("retention_policy_json"), RetentionPolicy.class),
                readJson(resultSet.getString("artifacts_json"), ARTIFACTS_TYPE),
                readJson(resultSet.getString("caseroot_bundle_json"), CaseRootBundle.class),
                readJson(resultSet.getString("summary_json"), AnalysisSummary.class),
                resultSet.getString("failure_reason")
        );
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
                        source_type VARCHAR(40) NOT NULL,
                        source_location TEXT NOT NULL,
                        original_file_name VARCHAR(255),
                        application VARCHAR(200),
                        environment VARCHAR(120),
                        requested_parser_profile VARCHAR(120),
                        selected_parser_plugin VARCHAR(160),
                        runtime_descriptor_json TEXT,
                        status VARCHAR(40) NOT NULL,
                        created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
                        retention_policy_json TEXT NOT NULL,
                        artifacts_json TEXT NOT NULL,
                        caseroot_bundle_json TEXT,
                        summary_json TEXT,
                        failure_reason TEXT
                    )
                    """.formatted(table("analysis_job")));

            schemaReady = true;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize job persistence payload.", exception);
        }
    }

    private <T> T readJson(String value, Class<T> type) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to deserialize job persistence payload.", exception);
        }
    }

    private <T> T readJson(String value, TypeReference<T> typeReference) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, typeReference);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to deserialize job persistence payload.", exception);
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
