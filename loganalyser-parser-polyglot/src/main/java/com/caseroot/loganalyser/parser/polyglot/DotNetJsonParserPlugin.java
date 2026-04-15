package com.caseroot.loganalyser.parser.polyglot;

import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.spi.ParserPlugin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DotNetJsonParserPlugin implements ParserPlugin {

    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "dotnet",
            "structured-json",
            "dotnet_json",
            "1.0.0"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String pluginId() {
        return "dotnet-json-parser";
    }

    @Override
    public String displayName() {
        return ".NET JSON Parser";
    }

    @Override
    public RuntimeDescriptor runtimeDescriptor() {
        return RUNTIME;
    }

    @Override
    public Set<String> supportedProfiles() {
        return Set.of("dotnet_json");
    }

    @Override
    public boolean supportsProfile(String profile) {
        return supportedProfiles().contains(profile);
    }

    @Override
    public int detectionConfidence(List<String> sampleLines) {
        long hits = sampleLines.stream()
                .filter(line -> line.startsWith("{") && (line.contains("\"SourceContext\"") || line.contains("\"@m\"") || line.contains("\"Exception\"")))
                .count();
        return (int) Math.min(100, hits * 25);
    }

    @Override
    public LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent) {
        try {
            JsonNode root = objectMapper.readTree(reconstructedLogEvent.rawEvent());
            String timestamp = text(root, "@t", "Timestamp", "timestamp");
            String level = normalizeLevel(text(root, "@l", "Level", "level"));
            String logger = text(root, "SourceContext", "Category", "Logger", "logger");
            String message = text(root, "@m", "RenderedMessage", "Message", "message");
            String exception = text(root, "@x", "Exception", "exception");
            String exceptionClass = exception == null ? null : PolyglotParserSupport.extractDotNetExceptionClass(exception);

            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.PARSED,
                    "dotnet_json_event",
                    timestamp,
                    level,
                    logger,
                    null,
                    message == null ? reconstructedLogEvent.rawEvent() : message,
                    reconstructedLogEvent.rawEvent(),
                    message == null ? reconstructedLogEvent.rawEvent() : message,
                    null,
                    null,
                    exceptionClass,
                    exceptionClass,
                    List.of(),
                    flatten(root)
            );
        } catch (Exception exception) {
            return new LogEvent(
                    UUID.randomUUID(),
                    jobId,
                    reconstructedLogEvent.sequence(),
                    reconstructedLogEvent.lineStart(),
                    reconstructedLogEvent.lineEnd(),
                    reconstructedLogEvent.sourceFile(),
                    RUNTIME,
                    ParseStatus.UNCLASSIFIED,
                    "dotnet_json_unclassified",
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
                    List.of(),
                    Map.of()
            );
        }
    }

    private String text(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = root.get(fieldName);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private String normalizeLevel(String level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case "Information" -> "INFO";
            case "Warning" -> "WARN";
            case "Error", "Critical" -> "ERROR";
            default -> level.toUpperCase();
        };
    }

    private Map<String, String> flatten(JsonNode root) {
        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value.isValueNode()) {
                values.put(entry.getKey(), value.asText());
            }
        }
        return Map.copyOf(values);
    }
}
