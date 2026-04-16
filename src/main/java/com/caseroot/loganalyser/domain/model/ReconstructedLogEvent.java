package com.caseroot.loganalyser.domain.model;

public record ReconstructedLogEvent(
        String sourceFile,
        long sequence,
        long lineStart,
        long lineEnd,
        String rawEvent
) {
}

