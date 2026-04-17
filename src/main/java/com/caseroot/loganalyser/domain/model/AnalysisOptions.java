package com.caseroot.loganalyser.domain.model;

import java.util.LinkedHashSet;
import java.util.List;

public record AnalysisOptions(
        Long largeGapHighlightThresholdMs,
        List<AnalysisFocus> focusSelections
) {
    public AnalysisOptions {
        focusSelections = normalizeFocusSelections(focusSelections);
    }

    public boolean includes(AnalysisFocus focus) {
        return focusSelections.contains(AnalysisFocus.ALL) || focusSelections.contains(focus);
    }

    private static List<AnalysisFocus> normalizeFocusSelections(List<AnalysisFocus> focusSelections) {
        if (focusSelections == null || focusSelections.isEmpty() || focusSelections.contains(AnalysisFocus.ALL)) {
            return List.of(AnalysisFocus.ALL);
        }
        return List.copyOf(new LinkedHashSet<>(focusSelections));
    }
}
