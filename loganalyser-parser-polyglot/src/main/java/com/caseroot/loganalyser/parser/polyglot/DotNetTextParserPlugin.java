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

public final class DotNetTextParserPlugin implements ParserPlugin {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "^(?<timestamp>\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{3,7})?(?:Z|[+-]\\d{2}:?\\d{2})?)\\s+(?:\\[(?<shortLevel>[A-Z]{3})]|(?<levelWord>Information|Warning|Error|Critical|Debug|Trace))\\s+(?<logger>[\\w.$]+)\\s*[:|-]\\s*(?<message>.*)$"
    );
    private static final Pattern MICROSOFT_PATTERN = Pattern.compile(
            "^(?<level>trce|dbug|info|warn|fail|crit):\\s+(?<logger>[^\\[]+)(?:\\[(?<eventId>\\d+)])?\\s*(?<message>.*)$"
    );
    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "dotnet",
            "microsoft-extensions-logging",
            "dotnet_text",
            "1.0.0"
    );

    @Override
    public String pluginId() {
        return "dotnet-text-parser";
    }

    @Override
    public String displayName() {
        return ".NET Text Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("dotnet_text");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream()
                .filter(line -> TIMESTAMP_PATTERN.matcher(line).matches() || MICROSOFT_PATTERN.matcher(line).matches())
                .count();
        return (int) Math.min(95, hits * 18);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        String firstLine = reconstructedLogEvent.rawEvent().lines().findFirst().orElse(reconstructedLogEvent.rawEvent());
        Matcher timestampMatcher = TIMESTAMP_PATTERN.matcher(firstLine);
        Matcher microsoftMatcher = MICROSOFT_PATTERN.matcher(firstLine);
        String exceptionClass = PolyglotParserSupport.extractDotNetExceptionClass(reconstructedLogEvent.rawEvent());

        if (timestampMatcher.matches()) {
            String level = timestampMatcher.group("shortLevel") != null
                    ? normalizeShortLevel(timestampMatcher.group("shortLevel"))
                    : normalizeWordLevel(timestampMatcher.group("levelWord"));
            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.PARSED,
                    "dotnet_text_event",
                    timestampMatcher.group("timestamp"),
                    level,
                    timestampMatcher.group("logger"),
                    null,
                    timestampMatcher.group("message"),
                    reconstructedLogEvent.rawEvent(),
                    timestampMatcher.group("message"),
                    null,
                    null,
                    exceptionClass,
                    exceptionClass,
                    List.of(),
                    Map.of()
            );
        }

        if (microsoftMatcher.matches()) {
            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.PARTIAL,
                    "dotnet_text_event",
                    null,
                    normalizeShortLevel(microsoftMatcher.group("level").toUpperCase()),
                    microsoftMatcher.group("logger").trim(),
                    null,
                    microsoftMatcher.group("message"),
                    reconstructedLogEvent.rawEvent(),
                    microsoftMatcher.group("message"),
                    null,
                    null,
                    exceptionClass,
                    exceptionClass,
                    List.of(),
                    microsoftMatcher.group("eventId") == null ? Map.of() : Map.of("eventId", microsoftMatcher.group("eventId"))
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
                "dotnet_unclassified",
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
                List.of(),
                Map.of()
        );
    }

    private String normalizeShortLevel(String level) {
        return switch (level.toUpperCase()) {
            case "ERR", "FAIL", "CRIT" -> "ERROR";
            case "WRN", "WARN" -> "WARN";
            case "DBG", "DBUG" -> "DEBUG";
            case "TRC", "TRCE" -> "TRACE";
            default -> "INFO";
        };
    }

    private String normalizeWordLevel(String level) {
        return switch (level) {
            case "Error", "Critical" -> "ERROR";
            case "Warning" -> "WARN";
            case "Debug" -> "DEBUG";
            case "Trace" -> "TRACE";
            default -> "INFO";
        };
    }
}
