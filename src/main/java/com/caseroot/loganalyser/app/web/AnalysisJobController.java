package com.caseroot.loganalyser.app.web;

import com.caseroot.loganalyser.core.application.AnalysisApplicationService;
import com.caseroot.loganalyser.core.application.CreateAnalysisJobCommand;
import com.caseroot.loganalyser.domain.model.AnalysisJob;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.DashboardOverview;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.EventQueryResult;
import com.caseroot.loganalyser.domain.model.JobComparisonResult;
import com.caseroot.loganalyser.domain.model.ModuleDescriptor;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.SourceType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalysisJobController {

    private final AnalysisApplicationService analysisApplicationService;

    public AnalysisJobController(AnalysisApplicationService analysisApplicationService) {
        this.analysisApplicationService = analysisApplicationService;
    }

    @PostMapping("/jobs")
    public ResponseEntity<AnalysisJob> createJob(@RequestBody CreateJobRequest request) {
        validate(request);

        AnalysisJob job = analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                request.sourceType(),
                request.sourceLocation(),
                deriveOriginalFileName(request),
                request.application(),
                request.environment(),
                request.requestedParserProfile()
        ));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @PostMapping("/jobs/upload")
    public ResponseEntity<AnalysisJob> uploadJob(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "environment", required = false) String environment,
            @RequestParam(value = "requestedParserProfile", required = false) String requestedParserProfile
    ) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is required.");
        }

        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("loganalyser-upload-", ".log");
            file.transferTo(tempFile);

            AnalysisJob job = analysisApplicationService.createJob(new CreateAnalysisJobCommand(
                    SourceType.FILE_UPLOAD,
                    tempFile.toString(),
                    file.getOriginalFilename(),
                    application,
                    environment,
                    requestedParserProfile
            ));

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to stage uploaded file.", exception);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                    // Best effort cleanup of the controller-owned temp file.
                }
            }
        }
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<AnalysisJob> getJob(@PathVariable UUID jobId) {
        return analysisApplicationService.getJob(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs")
    public List<AnalysisJob> listJobs() {
        return analysisApplicationService.listJobs();
    }

    @GetMapping("/jobs/{jobId}/summary")
    public ResponseEntity<AnalysisSummary> getJobSummary(@PathVariable UUID jobId) {
        return analysisApplicationService.getJob(jobId)
                .map(AnalysisJob::summary)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/jobs/{jobId}/events")
    public ResponseEntity<EventQueryResult> queryEvents(
            @PathVariable UUID jobId,
            @RequestParam(value = "level", required = false) String level,
            @RequestParam(value = "parseStatus", required = false) String parseStatus,
            @RequestParam(value = "loggerContains", required = false) String loggerContains,
            @RequestParam(value = "exceptionClass", required = false) String exceptionClass,
            @RequestParam(value = "sourceFile", required = false) String sourceFile,
            @RequestParam(value = "contains", required = false) String contains,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok(analysisApplicationService.queryEvents(jobId, new EventQueryFilter(
                blankToNull(level),
                parseStatus == null || parseStatus.isBlank() ? null : ParseStatus.valueOf(parseStatus.trim().toUpperCase()),
                blankToNull(loggerContains),
                blankToNull(exceptionClass),
                blankToNull(sourceFile),
                blankToNull(contains),
                limit == null ? 100 : limit
        )));
    }

    @PostMapping("/compare")
    public ResponseEntity<JobComparisonResult> compareJobs(@RequestBody CompareJobsRequest request) {
        if (request == null || request.jobIds().isEmpty()) {
            throw new IllegalArgumentException("jobIds are required.");
        }
        return ResponseEntity.ok(analysisApplicationService.compareJobs(request.jobIds()));
    }

    @GetMapping("/dashboard/overview")
    public ResponseEntity<DashboardOverview> dashboardOverview() {
        return ResponseEntity.ok(analysisApplicationService.dashboardOverview());
    }

    @GetMapping("/jobs/{jobId}/artifacts/{artifactKey}")
    public ResponseEntity<Resource> downloadArtifact(
            @PathVariable UUID jobId,
            @PathVariable String artifactKey
    ) {
        AnalysisJob job = analysisApplicationService.getJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown job: " + jobId));

        ArtifactDescriptor artifact = job.artifacts().get(artifactKey);
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }

        Path artifactPath = Path.of(artifact.location());
        if (!Files.exists(artifactPath) || Files.isDirectory(artifactPath)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (artifact.contentType() != null && !artifact.contentType().isBlank()) {
            mediaType = MediaType.parseMediaType(artifact.contentType());
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("Content-Disposition", ContentDisposition.attachment()
                        .filename(downloadName(job, artifact, artifactPath))
                        .build()
                        .toString())
                .body(new FileSystemResource(artifactPath));
    }

    @GetMapping("/modules")
    public List<ModuleDescriptor> getModules() {
        return analysisApplicationService.listModules();
    }

    private void validate(CreateJobRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required.");
        }
        if (request.sourceType() == null) {
            throw new IllegalArgumentException("sourceType is required.");
        }
        if (request.sourceLocation() == null || request.sourceLocation().isBlank()) {
            throw new IllegalArgumentException("sourceLocation is required.");
        }
    }

    private String deriveOriginalFileName(CreateJobRequest request) {
        if (request.originalFileName() != null && !request.originalFileName().isBlank()) {
            return request.originalFileName();
        }
        return Path.of(request.sourceLocation()).getFileName().toString();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String downloadName(AnalysisJob job, ArtifactDescriptor artifact, Path artifactPath) {
        String fileName = artifactPath.getFileName().toString();
        if (fileName.contains(".")) {
            return job.jobId() + "-" + artifact.type() + fileName.substring(fileName.indexOf('.'));
        }
        return job.jobId() + "-" + artifact.type();
    }
}
