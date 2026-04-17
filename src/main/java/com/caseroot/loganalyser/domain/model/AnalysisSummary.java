package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
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
