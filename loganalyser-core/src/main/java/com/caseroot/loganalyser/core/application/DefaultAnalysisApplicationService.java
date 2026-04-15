package com.caseroot.loganalyser.core.application;

import com.caseroot.loganalyser.core.ingest.FileAnalysisProcessor;
import com.caseroot.loganalyser.core.ingest.LineSampler;
import com.caseroot.loganalyser.core.query.ArtifactEventQueryService;
import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisJobStatus;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.DashboardOverview;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.EventQueryResult;
import com.caseroot.loganalyser.domain.model.JobComparisonEntry;
import com.caseroot.loganalyser.domain.model.JobComparisonResult;
import com.caseroot.loganalyser.domain.model.ModuleDescriptor;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SharedExceptionSummary;
import com.caseroot.loganalyser.domain.model.SharedSignatureSummary;
import com.caseroot.loganalyser.spi.ParquetArtifactExporter;
import com.caseroot.loganalyser.spi.AnalysisSummaryStore;
import com.caseroot.loganalyser.spi.ArtifactStorage;
import com.caseroot.loganalyser.spi.CaseRootExportBuilder;
import com.caseroot.loganalyser.spi.JobRepository;
import com.caseroot.loganalyser.spi.ParserPlugin;
import com.caseroot.loganalyser.spi.ParserRegistry;
import com.caseroot.loganalyser.spi.RetentionPolicyResolver;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class DefaultAnalysisApplicationService implements AnalysisApplicationService {

    private static final Logger log = Logger.getLogger(DefaultAnalysisApplicationService.class.getName());

    private final ParserRegistry parserRegistry;
    private final ArtifactStorage artifactStorage;
    private final CaseRootExportBuilder caseRootExportBuilder;
    private final JobRepository jobRepository;
    private final AnalysisSummaryStore analysisSummaryStore;
    private final RetentionPolicyResolver retentionPolicyResolver;
    private final Clock clock;
    private final Executor analysisExecutor;
    private final LineSampler lineSampler;
    private final FileAnalysisProcessor fileAnalysisProcessor;
    private final ArtifactEventQueryService artifactEventQueryService;
    private final ParquetArtifactExporter parquetArtifactExporter;

    public DefaultAnalysisApplicationService(
            ParserRegistry parserRegistry,
            ArtifactStorage artifactStorage,
            CaseRootExportBuilder caseRootExportBuilder,
            JobRepository jobRepository,
            AnalysisSummaryStore analysisSummaryStore,
            RetentionPolicyResolver retentionPolicyResolver,
            Clock clock,
            Executor analysisExecutor,
            LineSampler lineSampler,
            FileAnalysisProcessor fileAnalysisProcessor,
            ArtifactEventQueryService artifactEventQueryService,
            ParquetArtifactExporter parquetArtifactExporter
    ) {
        this.parserRegistry = parserRegistry;
        this.artifactStorage = artifactStorage;
        this.caseRootExportBuilder = caseRootExportBuilder;
        this.jobRepository = jobRepository;
        this.analysisSummaryStore = analysisSummaryStore;
        this.retentionPolicyResolver = retentionPolicyResolver;
        this.clock = clock;
        this.analysisExecutor = analysisExecutor;
        this.lineSampler = lineSampler;
        this.fileAnalysisProcessor = fileAnalysisProcessor;
        this.artifactEventQueryService = artifactEventQueryService;
        this.parquetArtifactExporter = parquetArtifactExporter;
    }

    @Override
    public AnalysisJob createJob(CreateAnalysisJobCommand command) {
        var requestedProfile = Optional.ofNullable(command.requestedParserProfile())
                .map(String::trim)
                .filter(value -> !value.isEmpty());

        RetentionPolicy retentionPolicy = retentionPolicyResolver.resolve(command.sourceType());
        UUID jobId = UUID.randomUUID();
        Instant now = clock.instant();

        ArtifactDescriptor rawArtifact = artifactStorage.allocateRawLog(jobId, command.originalFileName(), retentionPolicy);
        ArtifactDescriptor parsedArtifact = artifactStorage.allocateParsedEvents(jobId, retentionPolicy);
        ArtifactDescriptor parquetArtifact = parquetArtifactExporter.enabled()
                ? artifactStorage.allocateParquetEvents(jobId, retentionPolicy)
                : null;
        ArtifactDescriptor summaryArtifact = artifactStorage.allocateSummary(jobId, retentionPolicy);
        ArtifactDescriptor caseRootArtifact = artifactStorage.allocateCaseRootBundle(jobId, retentionPolicy);
        Path rawPath = Path.of(rawArtifact.location());
        Map<String, ArtifactDescriptor> artifacts = buildArtifacts(rawArtifact, parsedArtifact, parquetArtifact, summaryArtifact, caseRootArtifact);

        AnalysisJob acceptedJob = new AnalysisJob(
                jobId,
                command.sourceType(),
                command.sourceLocation(),
                command.originalFileName(),
                command.application(),
                command.environment(),
                requestedProfile.orElse(null),
                null,
                null,
                AnalysisJobStatus.ACCEPTED,
                now,
                now,
                retentionPolicy,
                artifacts,
                null,
                null,
                null
        );

        jobRepository.save(acceptedJob);

        try {
            List<Path> analysisInputs = stageInputs(command, rawPath);
            List<String> sampleLines = lineSampler.sample(analysisInputs, 200);
            ParserPlugin parserPlugin = parserRegistry.resolve(requestedProfile, sampleLines);

            AnalysisJob runningJob = new AnalysisJob(
                    jobId,
                    command.sourceType(),
                    command.sourceLocation(),
                    command.originalFileName(),
                    command.application(),
                    command.environment(),
                    requestedProfile.orElse(null),
                    parserPlugin.pluginId(),
                    parserPlugin.runtimeDescriptor(),
                    AnalysisJobStatus.RUNNING,
                    now,
                    now,
                    retentionPolicy,
                    artifacts,
                    null,
                    null,
                    null
            );

            jobRepository.save(runningJob);
            analysisExecutor.execute(() -> processJob(
                    command,
                    requestedProfile.orElse(null),
                    jobId,
                    now,
                    retentionPolicy,
                    analysisInputs,
                    parsedArtifact,
                    parquetArtifact,
                    summaryArtifact,
                    caseRootArtifact,
                    artifacts,
                    parserPlugin
            ));
            return runningJob;
        } catch (RuntimeException exception) {
            jobRepository.save(buildFailedJob(
                    command,
                    requestedProfile.orElse(null),
                    jobId,
                    now,
                    retentionPolicy,
                    artifacts,
                    null,
                    null,
                    exception
            ));
            throw exception;
        }
    }

    @Override
    public Optional<AnalysisJob> getJob(UUID jobId) {
        return jobRepository.findById(jobId);
    }

    @Override
    public List<AnalysisJob> listJobs() {
        return jobRepository.findAll();
    }

    @Override
    public EventQueryResult queryEvents(UUID jobId, EventQueryFilter filter) {
        AnalysisJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));

        ArtifactDescriptor parsedArtifact = Optional.ofNullable(job.artifacts().get("parsed-events"))
                .orElseThrow(() -> new IllegalArgumentException("Parsed artifact not available for job: " + jobId));

        return artifactEventQueryService.query(jobId, parsedArtifact, filter);
    }

    @Override
    public JobComparisonResult compareJobs(List<UUID> jobIds) {
        List<UUID> distinctJobIds = jobIds == null ? List.of() : jobIds.stream().distinct().toList();
        if (distinctJobIds.size() < 2) {
            throw new IllegalArgumentException("At least two job ids are required for comparison.");
        }

        List<AnalysisJob> jobs = distinctJobIds.stream()
                .map(jobId -> jobRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId)))
                .toList();

        for (AnalysisJob job : jobs) {
            if (job.summary() == null) {
                throw new IllegalArgumentException("Job is not completed and cannot be compared: " + job.jobId());
            }
        }

        Map<String, Long> aggregatedLevels = new LinkedHashMap<>();
        Map<String, SharedSignatureAggregate> signatureAggregates = new HashMap<>();
        Map<String, SharedExceptionAggregate> exceptionAggregates = new HashMap<>();

        for (AnalysisJob job : jobs) {
            job.summary().levelCounts().forEach((level, count) -> aggregatedLevels.merge(level, count, Long::sum));
            for (var signature : job.summary().topSignatures()) {
                signatureAggregates.computeIfAbsent(signature.signatureHash(), ignored -> new SharedSignatureAggregate(
                        signature.signatureHash(),
                        signature.normalizedMessage()
                )).record(job.jobId(), signature.count());
            }
            for (var exception : job.summary().topExceptions()) {
                String key = (exception.exceptionClass() == null ? "" : exception.exceptionClass()) + "|"
                        + (exception.rootCauseClass() == null ? "" : exception.rootCauseClass());
                exceptionAggregates.computeIfAbsent(key, ignored -> new SharedExceptionAggregate(
                        exception.exceptionClass(),
                        exception.rootCauseClass()
                )).record(job.jobId(), exception.count());
            }
        }

        return new JobComparisonResult(
                distinctJobIds,
                jobs.stream().map(this::toComparisonEntry).toList(),
                signatureAggregates.values().stream()
                        .filter(aggregate -> aggregate.jobCount() >= 2)
                        .sorted(Comparator.comparingLong(SharedSignatureAggregate::totalCount).reversed())
                        .map(SharedSignatureAggregate::toSummary)
                        .limit(20)
                        .toList(),
                exceptionAggregates.values().stream()
                        .filter(aggregate -> aggregate.jobCount() >= 2)
                        .sorted(Comparator.comparingLong(SharedExceptionAggregate::totalCount).reversed())
                        .map(SharedExceptionAggregate::toSummary)
                        .limit(20)
                        .toList(),
                aggregatedLevels
        );
    }

    @Override
    public DashboardOverview dashboardOverview() {
        List<AnalysisJob> jobs = jobRepository.findAll();
        Map<String, Long> jobsByApplication = new LinkedHashMap<>();
        Map<String, Long> jobsByRuntimeFamily = new LinkedHashMap<>();
        long totalEventsAcrossCompletedJobs = 0L;
        long acceptedJobs = 0L;
        long runningJobs = 0L;
        long completedJobs = 0L;
        long failedJobs = 0L;

        for (AnalysisJob job : jobs) {
            if (job.application() != null && !job.application().isBlank()) {
                jobsByApplication.merge(job.application(), 1L, Long::sum);
            }
            if (job.runtimeDescriptor() != null && job.runtimeDescriptor().family() != null) {
                jobsByRuntimeFamily.merge(job.runtimeDescriptor().family(), 1L, Long::sum);
            }
            switch (job.status()) {
                case ACCEPTED -> acceptedJobs++;
                case RUNNING -> runningJobs++;
                case COMPLETED -> completedJobs++;
                case FAILED -> failedJobs++;
            }
            if (job.summary() != null) {
                totalEventsAcrossCompletedJobs += job.summary().counts().totalEvents();
            }
        }

        return new DashboardOverview(
                jobs.size(),
                acceptedJobs,
                runningJobs,
                completedJobs,
                failedJobs,
                totalEventsAcrossCompletedJobs,
                jobsByApplication,
                jobsByRuntimeFamily,
                jobs.stream()
                        .sorted(Comparator.comparing(AnalysisJob::updatedAt).reversed())
                        .limit(10)
                        .map(this::toComparisonEntry)
                        .toList()
        );
    }

    @Override
    public List<ModuleDescriptor> listModules() {
        List<ModuleDescriptor> modules = new ArrayList<>();

        for (ParserPlugin plugin : parserRegistry.plugins()) {
            modules.add(new ModuleDescriptor(
                    plugin.displayName(),
                    "parser",
                    plugin.getClass().getName(),
                    plugin.supportedProfiles().stream().sorted().toList(),
                    "Runtime=%s/%s".formatted(
                            plugin.runtimeDescriptor().family(),
                            plugin.runtimeDescriptor().framework()
                    )
            ));
        }

        modules.add(new ModuleDescriptor(
                "artifact-storage",
                "storage",
                artifactStorage.getClass().getName(),
                List.of(artifactStorage.storageId(), "summary-artifact"),
                "Allocates raw, parsed, summary, and CaseRoot artifact locations outside the database."
        ));

        modules.add(new ModuleDescriptor(
                "caseroot-export",
                "export",
                caseRootExportBuilder.getClass().getName(),
                List.of(caseRootExportBuilder.builderId()),
                "Builds a compact CaseRoot-facing bundle descriptor."
        ));

        modules.add(new ModuleDescriptor(
                "analysis-summary-store",
                "persistence",
                analysisSummaryStore.getClass().getName(),
                List.of(analysisSummaryStore.storeId(), "summary-aggregates"),
                "Persists compact summary aggregates outside the raw log artifact path."
        ));

        modules.add(new ModuleDescriptor(
                "event-query",
                "query",
                artifactEventQueryService.getClass().getName(),
                List.of("gzip-ndjson-scan", "level-filter", "exception-filter", "source-file-filter"),
                "Reads parsed NDJSON artifacts and serves filtered event queries."
        ));

        modules.add(new ModuleDescriptor(
                "parquet-export",
                "export",
                parquetArtifactExporter.getClass().getName(),
                List.of(parquetArtifactExporter.exporterId(), parquetArtifactExporter.enabled() ? "enabled" : "disabled"),
                "Exports parsed event artifacts to Parquet when enabled."
        ));

        modules.add(new ModuleDescriptor(
                "analysis-orchestration",
                "core",
                getClass().getName(),
                List.of("job-creation", "retention-resolution", "artifact-allocation", "directory-batch-ingestion", "event-query", "job-comparison", "dashboard-overview"),
                "Coordinates module wiring without pulling Spring into the core domain."
        ));

        return List.copyOf(modules);
    }

    private List<Path> stageInputs(CreateAnalysisJobCommand command, Path rawArtifactPath) {
        return switch (command.sourceType()) {
            case FILE_PATH, FILE_UPLOAD -> List.of(copyToRawArtifact(Path.of(command.sourceLocation()), rawArtifactPath));
            case DIRECTORY -> stageDirectoryInputs(Path.of(command.sourceLocation()), rawArtifactPath);
        };
    }

    private Path copyToRawArtifact(Path sourcePath, Path rawArtifactPath) {
        try {
            if (!Files.exists(sourcePath)) {
                throw new IllegalArgumentException("Source log file does not exist: " + sourcePath);
            }
            if (sourcePath.equals(rawArtifactPath)) {
                return rawArtifactPath;
            }
            Files.createDirectories(rawArtifactPath.getParent());
            Files.copy(sourcePath, rawArtifactPath, StandardCopyOption.REPLACE_EXISTING);
            return rawArtifactPath;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to stage raw log file to " + rawArtifactPath, exception);
        }
    }

    private List<Path> stageDirectoryInputs(Path sourceDirectory, Path rawArtifactPath) {
        if (!Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDirectory);
        }

        Path stagedDirectory = rawArtifactPath.getParent().resolve("directory-files");
        List<Path> stagedFiles = new ArrayList<>();

        try (Stream<Path> pathStream = Files.walk(sourceDirectory)) {
            List<Path> sourceFiles = pathStream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();

            if (sourceFiles.isEmpty()) {
                throw new IllegalArgumentException("Source directory contains no regular files: " + sourceDirectory);
            }

            Files.createDirectories(stagedDirectory);
            Files.createDirectories(rawArtifactPath.getParent());

            try (BufferedWriter writer = Files.newBufferedWriter(rawArtifactPath)) {
                for (Path sourceFile : sourceFiles) {
                    Path relativePath = sourceDirectory.relativize(sourceFile);
                    Path targetPath = stagedDirectory.resolve(relativePath);
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourceFile, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    stagedFiles.add(targetPath);
                    writer.write(relativePath.toString());
                    writer.newLine();
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to stage directory inputs from " + sourceDirectory, exception);
        }

        return List.copyOf(stagedFiles);
    }

    private void processJob(
            CreateAnalysisJobCommand command,
            String requestedProfile,
            UUID jobId,
            Instant createdAt,
            RetentionPolicy retentionPolicy,
            List<Path> inputPaths,
            ArtifactDescriptor parsedArtifact,
            ArtifactDescriptor parquetArtifact,
            ArtifactDescriptor summaryArtifact,
            ArtifactDescriptor caseRootArtifact,
            Map<String, ArtifactDescriptor> artifacts,
            ParserPlugin parserPlugin
    ) {
        try {
            AnalysisSummary summary = fileAnalysisProcessor.process(
                    jobId,
                    inputPaths,
                    parserPlugin,
                    parsedArtifact,
                    summaryArtifact
            );

            if (parquetArtifact != null) {
                parquetArtifactExporter.export(parsedArtifact, parquetArtifact);
            }

            var caseRootBundle = caseRootExportBuilder.buildSkeletonBundle(
                    jobId,
                    command.application(),
                    command.environment(),
                    parserPlugin.runtimeDescriptor(),
                    summary,
                    caseRootArtifact,
                    retentionPolicy
            );

            AnalysisJob completedJob = new AnalysisJob(
                    jobId,
                    command.sourceType(),
                    command.sourceLocation(),
                    command.originalFileName(),
                    command.application(),
                    command.environment(),
                    requestedProfile,
                    parserPlugin.pluginId(),
                    parserPlugin.runtimeDescriptor(),
                    AnalysisJobStatus.COMPLETED,
                    createdAt,
                    clock.instant(),
                    retentionPolicy,
                    artifacts,
                    caseRootBundle,
                    summary,
                    null
            );

            analysisSummaryStore.persist(completedJob);
            jobRepository.save(completedJob);
        } catch (RuntimeException exception) {
            log.log(Level.SEVERE, "Analysis job " + jobId + " failed after parser resolution using plugin "
                    + parserPlugin.pluginId() + ".", exception);
            jobRepository.save(buildFailedJob(
                    command,
                    requestedProfile,
                    jobId,
                    createdAt,
                    retentionPolicy,
                    artifacts,
                    parserPlugin.pluginId(),
                    parserPlugin.runtimeDescriptor(),
                    exception
            ));
        }
    }

    private AnalysisJob buildFailedJob(
            CreateAnalysisJobCommand command,
            String requestedProfile,
            UUID jobId,
            Instant createdAt,
            RetentionPolicy retentionPolicy,
            Map<String, ArtifactDescriptor> artifacts,
            String selectedParserPlugin,
            RuntimeDescriptor runtimeDescriptor,
            RuntimeException exception
    ) {
        return new AnalysisJob(
                jobId,
                command.sourceType(),
                command.sourceLocation(),
                command.originalFileName(),
                command.application(),
                command.environment(),
                requestedProfile,
                selectedParserPlugin,
                runtimeDescriptor,
                AnalysisJobStatus.FAILED,
                createdAt,
                clock.instant(),
                retentionPolicy,
                artifacts,
                null,
                null,
                exception.getMessage()
        );
    }

    private Map<String, ArtifactDescriptor> buildArtifacts(
            ArtifactDescriptor rawArtifact,
            ArtifactDescriptor parsedArtifact,
            ArtifactDescriptor parquetArtifact,
            ArtifactDescriptor summaryArtifact,
            ArtifactDescriptor caseRootArtifact
    ) {
        Map<String, ArtifactDescriptor> artifacts = new LinkedHashMap<>();
        artifacts.put("raw", rawArtifact);
        artifacts.put("parsed-events", parsedArtifact);
        if (parquetArtifact != null) {
            artifacts.put("parquet-events", parquetArtifact);
        }
        artifacts.put("summary", summaryArtifact);
        artifacts.put("caseroot-bundle", caseRootArtifact);
        return Map.copyOf(artifacts);
    }

    private JobComparisonEntry toComparisonEntry(AnalysisJob job) {
        AnalysisSummary summary = job.summary();
        return new JobComparisonEntry(
                job.jobId(),
                job.application(),
                job.environment(),
                job.runtimeDescriptor() == null ? null : job.runtimeDescriptor().family(),
                job.selectedParserPlugin(),
                summary == null ? 0L : summary.counts().totalEvents(),
                summary == null ? 0L : summary.counts().parsedEvents(),
                summary == null ? 0L : summary.counts().partialEvents(),
                summary == null ? 0L : summary.counts().unclassifiedEvents(),
                summary == null ? 0L : summary.levelCounts().getOrDefault("ERROR", 0L),
                summary == null ? 0L : summary.levelCounts().getOrDefault("WARN", 0L),
                summary == null ? 0L : summary.levelCounts().getOrDefault("INFO", 0L)
        );
    }

    private static final class SharedSignatureAggregate {
        private final String signatureHash;
        private final String normalizedMessage;
        private final Set<UUID> jobs = new HashSet<>();
        private long totalCount;

        private SharedSignatureAggregate(String signatureHash, String normalizedMessage) {
            this.signatureHash = signatureHash;
            this.normalizedMessage = normalizedMessage;
        }

        void record(UUID jobId, long count) {
            jobs.add(jobId);
            totalCount += count;
        }

        int jobCount() {
            return jobs.size();
        }

        long totalCount() {
            return totalCount;
        }

        SharedSignatureSummary toSummary() {
            return new SharedSignatureSummary(signatureHash, normalizedMessage, jobs.size(), totalCount);
        }
    }

    private static final class SharedExceptionAggregate {
        private final String exceptionClass;
        private final String rootCauseClass;
        private final Set<UUID> jobs = new HashSet<>();
        private long totalCount;

        private SharedExceptionAggregate(String exceptionClass, String rootCauseClass) {
            this.exceptionClass = exceptionClass;
            this.rootCauseClass = rootCauseClass;
        }

        void record(UUID jobId, long count) {
            jobs.add(jobId);
            totalCount += count;
        }

        int jobCount() {
            return jobs.size();
        }

        long totalCount() {
            return totalCount;
        }

        SharedExceptionSummary toSummary() {
            return new SharedExceptionSummary(exceptionClass, rootCauseClass, jobs.size(), totalCount);
        }
    }
}
