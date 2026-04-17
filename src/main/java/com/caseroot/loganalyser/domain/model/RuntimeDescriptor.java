package com.caseroot.loganalyser.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeDescriptor(
        String family,
        String framework,
        String profile,
        String profileVersion
) {
}
