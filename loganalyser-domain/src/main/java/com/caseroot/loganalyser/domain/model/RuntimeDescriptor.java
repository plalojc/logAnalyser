package com.caseroot.loganalyser.domain.model;

public record RuntimeDescriptor(
        String family,
        String framework,
        String profile,
        String profileVersion
) {
}

