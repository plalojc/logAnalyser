package com.caseroot.loganalyser.domain.model;

public record RetentionPolicy(
        int rawLogDays,
        int parsedArtifactDays,
        int caseRootBundleDays,
        int metadataDays
) {
}

