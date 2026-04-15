package com.caseroot.loganalyser.domain.model;

import java.util.UUID;

public record JobComparisonEntry(
        UUID jobId,
        String application,
        String environment,
        String runtimeFamily,
        String parserPluginId,
        long totalEvents,
        long parsedEvents,
        long partialEvents,
        long unclassifiedEvents,
        long errorEvents,
        long warnEvents,
        long infoEvents
) {
}
