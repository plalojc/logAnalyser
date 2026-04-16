package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultilineEventReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void groupsJavaStackTraceIntoSingleLogicalEvent() throws IOException {
        Path logFile = tempDir.resolve("sample.log");
        Files.writeString(logFile, """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed to place order
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                2026-04-13 10:15:31,000 INFO [main] com.acme.OrderService - Recovered
                """);

        List<ReconstructedLogEvent> events = new ArrayList<>();
        new MultilineEventReader().read(logFile, events::add);

        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).lineStart());
        assertEquals(3L, events.get(0).lineEnd());
        assertEquals(4L, events.get(1).lineStart());
        assertEquals(4L, events.get(1).lineEnd());
    }
}

