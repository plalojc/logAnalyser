package com.caseroot.loganalyser.parser.polyglot;

import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.spi.ParserPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PythonLoggingParserPlugin implements ParserPlugin {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3})?)\\s+(?<level>DEBUG|INFO|WARNING|ERROR|CRITICAL)\\s+(?<logger>[\\w.]+)\\s*[-:]\\s*(?<message>.*)$"
    );
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
            "^(?<level>DEBUG|INFO|WARNING|ERROR|CRITICAL):(?<logger>[\\w.]+):(?<message>.*)$"
    );
    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "python",
            "python-logging",
            "python_logging",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "python-logging-parser";
    }

    @Override
    public String displayName() {
        return "Python Logging Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("python_logging", "python_traceback");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream()
                .filter(line -> line.contains("Traceback (most recent call last):")
                        || TIMESTAMP_PATTERN.matcher(line).matches()
                        || SIMPLE_PATTERN.matcher(line).matches())
                .count();
        return (int) Math.min(95, hits * 20);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(firstLine);
        Matcher simpleMatcher = SIMPLE_PATTERN.matcher(firstLine);
        String exceptionClass = PolyglotParserSupport.extractPythonExceptionClass(reconstructedLogEvent.rawEvent());

        if (timestampMatcher.matches()) {
            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.PARSED,
                    "python_log_event",
                    timestampMatcher.group("timestamp"),
                    normalizeLevel(timestampMatcher.group("level")),
                    timestampMatcher.group("logger"),
                    null,
                    timestampMatcher.group("message"),
                    reconstructedLogEvent.rawEvent(),
                    timestampMatcher.group("message"),
                    null,
                    null,
                    exceptionClass,
                    exceptionClass,
                    PolyglotParserSupport.extractPythonFrames(reconstructedLogEvent.rawEvent()),
                    Map.of()
            );
        }

        if (simpleMatcher.matches() || exceptionClass != null) {
            String level = simpleMatcher.matches() ? normalizeLevel(simpleMatcher.group("level")) : "ERROR";
            String logger = simpleMatcher.matches() ? simpleMatcher.group("logger") : null;
            String message = simpleMatcher.matches() ? simpleMatcher.group("message") : reconstructedLogEvent.rawEvent();
            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.PARTIAL,
                    "python_traceback",
                    null,
                    level,
                    logger,
                    null,
                    message,
                    reconstructedLogEvent.rawEvent(),
                    message,
                    null,
                    null,
                    exceptionClass,
                    exceptionClass,
                    PolyglotParserSupport.extractPythonFrames(reconstructedLogEvent.rawEvent()),
                    Map.of()
            );
        }

        return new LogEvent(
                UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                RUNTIME,
                ParseStatus.UNCLASSIFIED,
                "python_unclassified",
                null,
                null,
                null,
                null,
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                null,
                null,
                exceptionClass,
                exceptionClass,
                PolyglotParserSupport.extractPythonFrames(reconstructedLogEvent.rawEvent()),
                Map.of()
        );
    }

    private String normalizeLevel(String value) {
        return "CRITICAL".equals(value) ? "ERROR" : value;
    }
}
