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

public final class WebSphereParserPlugin implements ParserPlugin {

    private static final Pattern WEBSPHERE_PATTERN = Pattern.compile(
            "^\\[(?<timestamp>[^]]+)]\\s+(?<thread>[0-9A-Fa-f]+)\\s+(?<logger>\\S+)\\s+(?<level>[A-Z])\\s+(?<message>.*)$"
    );
    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "java",
            "websphere",
            "websphere_systemout",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "websphere-parser";
    }

    @Override
    public String displayName() {
        return "WebSphere Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("websphere_systemout");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream().filter(line -> line.startsWith("[") && line.contains("] ")).count();
        return (int) Math.min(90, hits * 12);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher matcher = WEBSPHERE_PATTERN.matcher(firstLine);

        if (!matcher.matches()) {
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
                "websphere_log_event",
                matcher.group("timestamp"),
                normalizeLevel(matcher.group("level")),
                matcher.group("logger"),
                matcher.group("thread"),
                matcher.group("message"),
                reconstructedLogEvent.rawEvent(),
                matcher.group("message"),
                null,
                null,
                ParserSupport.extractExceptionClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractRootCauseClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractStackFrames(reconstructedLogEvent.rawEvent()),
                Map.of()
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
                "websphere_unclassified",
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

    private String normalizeLevel(String levelCode) {
        return switch (levelCode) {
            case "E" -> "ERROR";
            case "W" -> "WARN";
            case "I", "A" -> "INFO";
            case "D" -> "DEBUG";
            default -> levelCode;
        };
    }
}
