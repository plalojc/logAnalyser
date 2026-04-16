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

public final class GenericTimestampParserPlugin implements ParserPlugin {

    private static final Pattern GENERIC_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T ][0-9:.+,\\-Z]+)\\s+(?:(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+)?(?<message>.*)$"
    );

    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "generic",
            "timestamped-text",
            "generic_timestamped",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "generic-timestamp-parser";
    }

    @Override
    public String displayName() {
        return "Generic Timestamp Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("generic_timestamped");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long matches = sampleLines.stream().filter(line -> GENERIC_PATTERN.matcher(line).matches()).count();
        return (int) Math.min(70, matches * 10);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher matcher = GENERIC_PATTERN.matcher(firstLine);

        if (!matcher.matches()) {
            return new LogEvent(
                    UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                RUNTIME,
                ParseStatus.UNCLASSIFIED,
                "generic_unclassified",
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

        String message = matcher.group("message");
        return new LogEvent(
                UUID.randomUUID(),
                jobId,
                reconstructedLogEvent.sequence(),
                reconstructedLogEvent.lineStart(),
                reconstructedLogEvent.lineEnd(),
                reconstructedLogEvent.sourceFile(),
                RUNTIME,
                ParseStatus.PARTIAL,
                "generic_timestamped",
                matcher.group("timestamp"),
                matcher.group("level"),
                null,
                null,
                message,
                reconstructedLogEvent.rawEvent(),
                message,
                null,
                null,
                ParserSupport.extractExceptionClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractRootCauseClass(reconstructedLogEvent.rawEvent()),
                ParserSupport.extractStackFrames(reconstructedLogEvent.rawEvent()),
                Map.of()
        );
    }
}
