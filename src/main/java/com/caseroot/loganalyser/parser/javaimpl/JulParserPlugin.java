package com.caseroot.loganalyser.parser.javaimpl;

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

public final class JulParserPlugin implements ParserPlugin {

    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "^(?<timestamp>[A-Z][a-z]{2} \\d{1,2}, \\d{4} \\d{1,2}:\\d{2}:\\d{2} (?:AM|PM))\\s+(?<logger>[\\w.$]+)\\s+(?<method>[\\w$<>]+)$"
    );
    private static final Pattern MESSAGE_PATTERN = Pattern.compile(
            "^(?<level>SEVERE|WARNING|INFO|CONFIG|FINE|FINER|FINEST):\\s*(?<message>.*)$"
    );
    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "java",
            "java-util-logging",
            "jul_pattern",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "jul-parser";
    }

    @Override
    public String displayName() {
        return "JUL Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("jul_pattern");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream()
                .filter(line -> HEADER_PATTERN.matcher(line).matches() || line.startsWith("SEVERE:") || line.startsWith("WARNING:"))
                .count();
        return (int) Math.min(95, hits * 20);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        List<String> lines = reconstructedLogEvent.rawEvent().lines().toList();
        Matcher headerMatcher = lines.isEmpty() ? null : HEADER_PATTERN.matcher(lines.getFirst());
        Matcher messageMatcher = lines.size() < 2 ? null : MESSAGE_PATTERN.matcher(lines.get(1));

        if (headerMatcher == null || !headerMatcher.matches() || messageMatcher == null || !messageMatcher.matches()) {
            return fallback(jobId, reconstructedLogEvent);
        }

        return new LogEvent(
                UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                RUNTIME,
                ParseStatus.PARSED,
                "jul_log_event",
                headerMatcher.group("timestamp"),
                normalizeLevel(messageMatcher.group("level")),
                headerMatcher.group("logger"),
                null,
                messageMatcher.group("message"),
                reconstructedLogEvent.rawEvent(),
                messageMatcher.group("message"),
                null,
                null,
                ParserSupport.extractExceptionClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractRootCauseClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractStackFrames(reconstructedLogEvent.rawEvent()),
                Map.of("method", headerMatcher.group("method"))
        );
    }

    private LogEvent fallback(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        return new LogEvent(
                UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                RUNTIME,
                ParseStatus.UNCLASSIFIED,
                "jul_unclassified",
                null,
                null,
                null,
                null,
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                reconstructedLogEvent.rawEvent(),
                null,
                null,
                ParserSupport.extractExceptionClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractRootCauseClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractStackFrames(reconstructedLogEvent.rawEvent()),
                Map.of()
        );
    }

    private String normalizeLevel(String value) {
        return switch (value) {
            case "SEVERE" -> "ERROR";
            case "WARNING" -> "WARN";
            default -> value;
        };
    }
}
