package com.caseroot.loganalyser.domain.model;

import java.util.List;

public record ModuleDescriptor(
        String name,
        String category,
        String implementationClass,
        List<String> capabilities,
        String description
) {
    public ModuleDescriptor {
        capabilities = List.copyOf(capabilities);
    }
}

