package com.caseroot.loganalyser.storage.filesystem;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.spi.ArtifactStorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public final class FileSystemArtifactStorage implements ArtifactStorage {

    private final Path basePath;
    private final Clock clock;

    public FileSystemArtifactStorage(Path basePath, Clock clock) {
        this.basePath = basePath;
        this.clock = clock;
    }

    @Override
    public String storageId() {
        return "filesystem-artifact-storage";
    }

    @Override
    public ArtifactDescriptor allocateRawLog(UUID jobId, String originalFileName, RetentionPolicy retentionPolicy) {
        String safeFileName = sanitizeFileName(originalFileName);
        Path path = basePath.resolve("raw").resolve(jobId.toString()).resolve(safeFileName);
        createParentDirectories(path);
        return descriptor("raw-log", path, "text/plain", retentionPolicy.rawLogDays());
    }

    @Override
    public ArtifactDescriptor allocateParsedEvents(UUID jobId, RetentionPolicy retentionPolicy) {
        Path path = basePath.resolve("parsed").resolve(jobId.toString()).resolve("events.ndjson.gz");
        createParentDirectories(path);
        return descriptor("parsed-events", path, "application/gzip", retentionPolicy.parsedArtifactDays());
    }

    @Override
    public ArtifactDescriptor allocateParquetEvents(UUID jobId, RetentionPolicy retentionPolicy) {
        Path path = basePath.resolve("parsed").resolve(jobId.toString()).resolve("events.parquet");
        createParentDirectories(path);
        return descriptor("parquet-events", path, "application/octet-stream", retentionPolicy.parsedArtifactDays());
    }

    @Override
    public ArtifactDescriptor allocateSummary(UUID jobId, RetentionPolicy retentionPolicy) {
        Path path = basePath.resolve("summary").resolve(jobId.toString()).resolve("summary.json");
        createParentDirectories(path);
        return descriptor("summary", path, "application/json", retentionPolicy.parsedArtifactDays());
    }

    @Override
    public ArtifactDescriptor allocateCaseRootBundle(UUID jobId, RetentionPolicy retentionPolicy) {
        Path path = basePath.resolve("caseroot").resolve(jobId.toString()).resolve("caseroot_input.json");
        createParentDirectories(path);
        return descriptor("caseroot-bundle", path, "application/json", retentionPolicy.caseRootBundleDays());
    }

    private ArtifactDescriptor descriptor(String type, Path path, String contentType, int retentionDays) {
        Instant expiresAt = clock.instant().plus(retentionDays, ChronoUnit.DAYS);
        return new ArtifactDescriptor(type, path.toString(), contentType, expiresAt);
    }

    private void createParentDirectories(Path path) {
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to create artifact directory for " + path, exception);
        }
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "uploaded.log";
        }
        return originalFileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
