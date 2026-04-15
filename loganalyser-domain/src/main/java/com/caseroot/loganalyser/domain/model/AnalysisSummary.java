package com.caseroot.loganalyser.domain.model;

import java.util.List;
import java.util.Map;

public record AnalysisSummary(
        String parserPluginId,
        RuntimeDescriptor runtime,
        AnalysisSummaryCounts counts,
        Map<String, Long> levelCounts,
        GapStatistics gapStatistics,
        List<SignatureSummary> topSignatures,
        List<ExceptionSummary> topExceptions,
        List<String> warnings
) {
    public AnalysisSummary {
        levelCounts = Map.copyOf(levelCounts);
        topSignatures = List.copyOf(topSignatures);
        topExceptions = List.copyOf(topExceptions);
        warnings = List.copyOf(warnings);
    }
}
