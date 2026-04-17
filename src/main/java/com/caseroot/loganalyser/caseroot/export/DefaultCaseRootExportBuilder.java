package com.caseroot.loganalyser.caseroot.export;

import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.CaseRootBundle;
import com.caseroot.loganalyser.domain.model.EventSnippet;
import com.caseroot.loganalyser.domain.model.GapHighlight;
import com.caseroot.loganalyser.domain.model.RetentionPolicy;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SignatureSummary;
import com.caseroot.loganalyser.spi.CaseRootExportBuilder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DefaultCaseRootExportBuilder implements CaseRootExportBuilder {

    private final ObjectMapper objectMapper;

    public DefaultCaseRootExportBuilder() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String builderId() {
        return "default-caseroot-export-builder";
    }

    @Override
    public CaseRootBundle buildSkeletonBundle(
            UUID jobId,
            String application,
            String environment,
            RuntimeDescriptor runtimeDescriptor,
            AnalysisSummary summary,
            ArtifactDescriptor bundleArtifact,
            RetentionPolicy retentionPolicy
    ) {
        List<String> sourceFiles = collectSourceFiles(summary);
        String primarySourceFile = resolvePrimarySourceFile(sourceFiles);
        AnalysisSummary compactSummary = compactSummary(summary, primarySourceFile, sourceFiles.size() > 1);

        CaseRootBundle bundle = new CaseRootBundle(
                jobId,
                bundleArtifact.location(),
                primarySourceFile,
                sourceFiles.size() > 1 ? sourceFiles : null,
                List.of(
                        "signature_hash",
                        "package_name",
                        "runtime.family",
                        "runtime.profile",
                        "package_highlights",
                        "exception_class",
                        "sample_messages",
                        "sample_events",
                        "gap_highlights"
                ),
                List.of(
                        "package_groups",
                        "timeline_anomalies",
                        "coverage_summary",
                        "artifact_references"
                ),
                bundleArtifact.expiresAt(),
                compactSummary
        );

        writeBundle(bundle, bundleArtifact);
        return bundle;
    }

    private void writeBundle(CaseRootBundle bundle, ArtifactDescriptor artifactDescriptor) {
        Path path = Path.of(artifactDescriptor.location());

        try {
            Files.createDirectories(path.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), bundle);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write CaseRoot bundle to " + path, exception);
        }
    }

    private AnalysisSummary compactSummary(AnalysisSummary summary, String primarySourceFile, boolean multipleSourceFiles) {
        List<SignatureSummary> compactSignatures = summary.topSignatures().stream()
                .map(signature -> compactSignature(signature, primarySourceFile, multipleSourceFiles))
                .toList();

        return new AnalysisSummary(
                summary.parserPluginId(),
                summary.runtime(),
                summary.counts(),
                summary.levelCounts(),
                summary.gapStatistics(),
                compactSignatures,
                summary.topExceptions(),
                summary.warnings()
        );
    }

    private SignatureSummary compactSignature(SignatureSummary signature, String primarySourceFile, boolean multipleSourceFiles) {
        List<EventSnippet> compactSampleEvents = signature.sampleEvents().stream()
                .map(eventSnippet -> compactEventSnippet(eventSnippet, primarySourceFile, multipleSourceFiles))
                .toList();

        List<GapHighlight> compactGapHighlights = signature.gapHighlights().stream()
                .map(gapHighlight -> new GapHighlight(
                        gapHighlight.gapMs(),
                        gapHighlight.occurrenceCount(),
                        compactEventSnippet(gapHighlight.previousEvent(), primarySourceFile, multipleSourceFiles),
                        compactEventSnippet(gapHighlight.currentEvent(), primarySourceFile, multipleSourceFiles)
                ))
                .toList();

        return new SignatureSummary(
                signature.signatureHash(),
                signature.packageName(),
                signature.representativeLogger(),
                signature.firstSeenTimestamp(),
                signature.lastSeenTimestamp(),
                signature.count(),
                signature.levelCounts(),
                signature.exceptionCount(),
                signature.largeGapCount(),
                signature.sampleMessages(),
                compactSampleEvents,
                compactGapHighlights,
                signature.highlights()
        );
    }

    private EventSnippet compactEventSnippet(EventSnippet eventSnippet, String primarySourceFile, boolean multipleSourceFiles) {
        String sourceFile = eventSnippet.sourceFile();
        if (!multipleSourceFiles && primarySourceFile != null && primarySourceFile.equals(sourceFile)) {
            sourceFile = null;
        }

        String statement = eventSnippet.statement();
        String message = eventSnippet.message();
        if (message != null && statement != null && statement.contains(message)) {
            message = null;
        }

        String logger = eventSnippet.logger();
        if (logger != null && statement != null && statement.contains(logger)) {
            logger = null;
        }

        String exceptionClass = eventSnippet.exceptionClass();
        String stackSummary = eventSnippet.stackSummary();
        if (exceptionClass != null && stackSummary != null && stackSummary.startsWith(exceptionClass)) {
            exceptionClass = null;
        }

        String rootCauseClass = eventSnippet.rootCauseClass();
        if (rootCauseClass != null && stackSummary != null && stackSummary.startsWith(rootCauseClass)) {
            rootCauseClass = null;
        }

        return new EventSnippet(
                sourceFile,
                eventSnippet.timestamp(),
                eventSnippet.level(),
                logger,
                message,
                statement,
                exceptionClass,
                rootCauseClass,
                stackSummary
        );
    }

    private List<String> collectSourceFiles(AnalysisSummary summary) {
        Map<String, Long> sourceFileCounts = new LinkedHashMap<>();

        for (SignatureSummary signature : summary.topSignatures()) {
            for (EventSnippet eventSnippet : signature.sampleEvents()) {
                sourceFileCounts.merge(eventSnippet.sourceFile(), 1L, Long::sum);
            }
            for (GapHighlight gapHighlight : signature.gapHighlights()) {
                sourceFileCounts.merge(gapHighlight.previousEvent().sourceFile(), 1L, Long::sum);
                sourceFileCounts.merge(gapHighlight.currentEvent().sourceFile(), 1L, Long::sum);
            }
        }

        return sourceFileCounts.keySet().stream()
                .filter(value -> value != null && !value.isBlank())
                .sorted()
                .toList();
    }

    private String resolvePrimarySourceFile(List<String> sourceFiles) {
        if (sourceFiles.size() == 1) {
            return sourceFiles.getFirst();
        }
        return null;
    }
}
