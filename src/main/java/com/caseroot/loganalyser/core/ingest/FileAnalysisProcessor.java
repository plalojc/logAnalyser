package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.AnalysisOptions;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.spi.ParserPlugin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

public final class FileAnalysisProcessor {

    private final ObjectMapper objectMapper;
    private final MultilineEventReader multilineEventReader;
    private final long largeGapHighlightThresholdMs;

    public FileAnalysisProcessor() {
        this(Duration.ofMinutes(1));
    }

    public FileAnalysisProcessor(Duration largeGapHighlightThreshold) {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.multilineEventReader = new MultilineEventReader();
        this.largeGapHighlightThresholdMs = largeGapHighlightThreshold == null
                ? Duration.ofMinutes(1).toMillis()
                : largeGapHighlightThreshold.toMillis();
    }

    public AnalysisSummary process(
            UUID jobId,
            java.util.List<Path> inputPaths,
            ParserPlugin parserPlugin,
            AnalysisOptions analysisOptions,
            ArtifactDescriptor parsedArtifact,
            ArtifactDescriptor summaryArtifact
    ) {
        long thresholdMs = analysisOptions != null && analysisOptions.largeGapHighlightThresholdMs() != null
                ? analysisOptions.largeGapHighlightThresholdMs()
                : largeGapHighlightThresholdMs;
        AnalysisSummaryAccumulator accumulator = new AnalysisSummaryAccumulator(thresholdMs, analysisOptions);
        Path parsedPath = Path.of(parsedArtifact.location());
        Path summaryPath = Path.of(summaryArtifact.location());

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(parsedPath)),
                StandardCharsets.UTF_8
        ))) {
            Consumer<ReconstructedLogEvent> eventConsumer = reconstructedLogEvent -> {
                LogEvent logEvent = parseEvent(jobId, parserPlugin, reconstructedLogEvent, accumulator);
                LogEvent enrichedEvent = accumulator.enrichAndRecord(reconstructedLogEvent, logEvent);

                try {
                    writer.write(objectMapper.writeValueAsString(enrichedEvent));
                    writer.newLine();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            };

            long[] sequenceCounter = {0L};
            for (Path inputPath : inputPaths) {
                multilineEventReader.read(inputPath, reconstructedLogEvent -> eventConsumer.accept(new ReconstructedLogEvent(
                        reconstructedLogEvent.sourceFile(),
                        ++sequenceCounter[0],
                        reconstructedLogEvent.lineStart(),
                        reconstructedLogEvent.lineEnd(),
                        reconstructedLogEvent.rawEvent()
                )));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to process input paths " + inputPaths, exception);
        }

        AnalysisSummary summary = accumulator.toSummary(parserPlugin.pluginId(), parserPlugin.runtimeDescriptor());

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(summaryPath.toFile(), summary);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to write summary artifact " + summaryPath, exception);
        }

        return summary;
    }

    private LogEvent parseEvent(
            UUID jobId,
            ParserPlugin parserPlugin,
            ReconstructedLogEvent reconstructedLogEvent,
            AnalysisSummaryAccumulator accumulator
    ) {
        try {
            return parserPlugin.parse(jobId, reconstructedLogEvent);
        } catch (RuntimeException exception) {
            accumulator.warning("Parser fallback used at event sequence " + reconstructedLogEvent.sequence());
            return buildFallbackEvent(jobId, reconstructedLogEvent, exception);
        }
    }

    private LogEvent buildFallbackEvent(UUID jobId, ReconstructedLogEvent reconstructedLogEvent, RuntimeException exception) {
        return new LogEvent(
                UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                new RuntimeDescriptor("generic", "raw", "raw_unclassified", "1.0.0"),
                ParseStatus.UNCLASSIFIED,
                "parser_error",
                null,
                null,
                null,
                null,
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                null,
                null,
                null,
                null,
                java.util.List.of(),
                Map.of("error", exception.getMessage() == null ? "unknown" : exception.getMessage())
        );
    }
}
