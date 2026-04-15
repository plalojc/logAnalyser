package com.caseroot.loganalyser.domain.model;

import java.util.List;
import java.util.UUID;

public record EventQueryResult(
        UUID jobId,
        List<LogEvent> events,
        long scannedEvents,
        int returnedEvents,
        boolean truncated
) {
    public EventQueryResult {
        events = List.copyOf(events);
    }
}
