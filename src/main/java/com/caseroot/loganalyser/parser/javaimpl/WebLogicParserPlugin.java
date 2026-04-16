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

public final class WebLogicParserPlugin implements ParserPlugin {

    private static final Pattern WEBLOGIC_PATTERN = Pattern.compile(
            "^####<(?<timestamp>[^>]*)>\\s+<(?<level>[^>]*)>\\s+<(?<subsystem>[^>]*)>\\s+<(?<machine>[^>]*)>\\s+<(?<server>[^>]*)>\\s+<(?<thread>[^>]*)>\\s+<(?<user>[^>]*)>\\s+<(?<tx>[^>]*)>\\s+<(?<diag>[^>]*)>\\s+<(?<messageId>[^>]*)>\\s+<(?<message>.*)>$"
    );
    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "java",
            "weblogic",
            "weblogic_server",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "weblogic-parser";
    }

    @Override
    public String displayName() {
        return "WebLogic Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("weblogic_server");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream().filter(line -> line.startsWith("####<")).count();
        return (int) Math.min(100, hits * 30);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher matcher = WEBLOGIC_PATTERN.matcher(firstLine);

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
                "weblogic_log_event",
                matcher.group("timestamp"),
                normalizeLevel(matcher.group("level")),
                matcher.group("subsystem"),
                matcher.group("thread"),
                matcher.group("message"),
                reconstructedLogEvent.rawEvent(),
                matcher.group("message"),
                null,
                null,
                ParserSupport.extractExceptionClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractRootCauseClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractStackFrames(reconstructedLogEvent.rawEvent()),
                Map.of(
                        "server", matcher.group("server"),
                        "machine", matcher.group("machine"),
                        "messageId", matcher.group("messageId")
                )
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
                "weblogic_unclassified",
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

    private String normalizeLevel(String level) {
        String normalized = level.toUpperCase();
        return switch (normalized) {
            case "ERROR", "CRITICAL", "ALERT", "EMERGENCY" -> "ERROR";
            case "WARNING", "WARN" -> "WARN";
            case "NOTICE" -> "INFO";
            default -> normalized;
        };
    }
}
