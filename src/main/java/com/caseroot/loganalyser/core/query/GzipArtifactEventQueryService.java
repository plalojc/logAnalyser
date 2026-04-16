package com.caseroot.loganalyser.core.query;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.EventQueryFilter;
import com.caseroot.loganalyser.domain.model.EventQueryResult;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

public final class GzipArtifactEventQueryService implements ArtifactEventQueryService {

    private final ObjectMapper objectMapper;

    public GzipArtifactEventQueryService() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public EventQueryResult query(UUID jobId, ArtifactDescriptor parsedArtifact, EventQueryFilter filter) {
        Path path = Path.of(parsedArtifact.location());
        List<LogEvent> matchedEvents = new ArrayList<>();
        long scannedEvents = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(path)),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEvent event = objectMapper.readValue(line, LogEvent.class);
                scannedEvents++;

                if (!matches(event, filter)) {
                    continue;
                }

                if (matchedEvents.size() >= filter.limit()) {
                    truncated = true;
                    break;
                }

                matchedEvents.add(event);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to query parsed artifact " + path, exception);
        }

        return new EventQueryResult(jobId, matchedEvents, scannedEvents, matchedEvents.size(), truncated);
    }

    private boolean matches(LogEvent event, EventQueryFilter filter) {
        if (filter.level() != null && !filter.level().equalsIgnoreCase(blankToEmpty(event.level()))) {
            return false;
        }
        if (filter.parseStatus() != null && filter.parseStatus() != event.parseStatus()) {
            return false;
        }
        if (filter.loggerContains() != null && !containsIgnoreCase(event.logger(), filter.loggerContains())) {
            return false;
        }
        if (filter.exceptionClass() != null && !filter.exceptionClass().equalsIgnoreCase(blankToEmpty(event.exceptionClass()))) {
            return false;
        }
        if (filter.sourceFile() != null && !filter.sourceFile().equalsIgnoreCase(blankToEmpty(event.sourceFile()))) {
            return false;
        }
        if (filter.contains() != null && !containsIgnoreCase(event.rawEvent(), filter.contains())
                && !containsIgnoreCase(event.message(), filter.contains())
                && !containsIgnoreCase(event.normalizedMessage(), filter.contains())) {
            return false;
        }
        return true;
    }

    private boolean containsIgnoreCase(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
