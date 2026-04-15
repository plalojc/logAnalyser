package com.caseroot.loganalyser.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record LogEvent(
        UUID eventId,
        UUID jobId,
        long sequence,
        long lineStart,
        long lineEnd,
        String sourceFile,
        RuntimeDescriptor runtime,
        ParseStatus parseStatus,
        String classification,
        String timestamp,
        String level,
        String logger,
        String thread,
        String message,
        String rawEvent,
        String normalizedMessage,
        String signatureHash,
        Long gapFromPreviousMs,
        String exceptionClass,
        String rootCauseClass,
        List<StackFrame> stackFrames,
        Map<String, String> keyValues
) {
    public LogEvent {
        stackFrames = List.copyOf(stackFrames);
        keyValues = Map.copyOf(keyValues);
    }
}
