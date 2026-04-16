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

public final class LegacyJavaParserPlugin implements ParserPlugin {

    private static final Pattern JAVA_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3,6})?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+"
                    + "(?<level>TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s+"
                    + "(?:\\[(?<thread>[^]]+)\\]\\s+)?"
                    + "(?:(?<logger>[\\w.$]+)\\s+-\\s+)?"
                    + "(?<message>.*)$"
    );

    private static final Set<String> SUPPORTED_PROFILES = Set.of(
            "legacy_java",
            "log4j_pattern",
            "logback_pattern",
            "tomcat_catalina"
    );

    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "java",
            "legacy-java-logs",
            "legacy_java",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "legacy-java-parser";
    }

    @Override
    public String displayName() {
        return "Legacy Java Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return SUPPORTED_PROFILES;
    }

    @Override
    public boolean supportsProfile(String profile) {
        return SUPPORTED_PROFILES.contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long matches = sampleLines.stream().filter(line -> JAVA_PATTERN.matcher(line).matches()).count();
        return (int) Math.min(100, matches * 15);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher matcher = JAVA_PATTERN.matcher(firstLine);

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
                    "java_unclassified",
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
                ParseStatus.PARSED,
                "java_log_event",
                matcher.group("timestamp"),
                matcher.group("level"),
                matcher.group("logger"),
                matcher.group("thread"),
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
