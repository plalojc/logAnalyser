package com.caseroot.loganalyser.domain.model;

import java.util.Map;

public record GapStatistics(
        long totalGaps,
        Long minGapMs,
        Long maxGapMs,
        Double avgGapMs,
        long outOfOrderGaps,
        long missingTimestampEvents,
        Map<String, Long> buckets
) {
    public GapStatistics {
        buckets = Map.copyOf(buckets);
    }
}

