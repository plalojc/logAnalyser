package com.caseroot.loganalyser.domain.model;

import java.time.Instant;

public record ArtifactDescriptor(
        String type,
        String location,
        String contentType,
        Instant expiresAt
) {
}
