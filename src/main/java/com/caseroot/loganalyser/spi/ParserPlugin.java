package com.caseroot.loganalyser.spi;

import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ParserPlugin {

    String pluginId();

    String displayName();

    RuntimeDescriptor runtimeDescriptor();

    Set<String> supportedProfiles();

    boolean supportsProfile(String profile);

    int detectionConfidence(List<String> sampleLines);

    LogEvent parse(UUID jobId, ReconstructedLogEvent reconstructedLogEvent);
}

