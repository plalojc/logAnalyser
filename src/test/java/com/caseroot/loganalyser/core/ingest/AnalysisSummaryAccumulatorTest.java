package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AnalysisSummaryAccumulatorTest {

    private static final RuntimeDescriptor RUNTIME = new RuntimeDescriptor(
            "java",
            "legacy-java-logs",
            "legacy_java",
            "1.0.0"
    );

    @Test
    void groupsEventsByPackageAndHighlightsExceptionsAndLongGaps() {
        AnalysisSummaryAccumulator accumulator = new AnalysisSummaryAccumulator();

        ReconstructedLogEvent firstRaw = new ReconstructedLogEvent("sample.log", 1, 1, 1, "first");
        ReconstructedLogEvent secondRaw = new ReconstructedLogEvent("sample.log", 2, 2, 2, "second");
        ReconstructedLogEvent thirdRaw = new ReconstructedLogEvent("sample.log", 3, 3, 3, "third");

        accumulator.enrichAndRecord(firstRaw, new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                1,
                1,
                "sample.log",
                RUNTIME,
                ParseStatus.PARSED,
                "java_log_event",
                "2026-04-13 10:15:30,123",
                "ERROR",
                "com.acme.orders.OrderService",
                "main",
                "Order 123 failed for request 550e8400-e29b-41d4-a716-446655440000",
                "2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Order 123 failed",
                "Order 123 failed for request 550e8400-e29b-41d4-a716-446655440000",
                null,
                null,
                "java.lang.IllegalStateException",
                "java.sql.SQLException",
                List.of(),
                Map.of()
        ));

        LogEvent enrichedSecond = accumulator.enrichAndRecord(secondRaw, new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                2,
                2,
                "sample.log",
                RUNTIME,
                ParseStatus.PARSED,
                "java_log_event",
                "2026-04-13 10:16:31,000",
                "INFO",
                "com.acme.orders.OrderService",
                "main",
                "Started redo recovery: checkpointLSN = 552977040341332, systemRedoLSN = 552977040335422",
                "2026-04-13 10:16:31,000 INFO [main] com.acme.OrderService - Started redo recovery",
                "Started redo recovery: checkpointLSN = 552977040341332, systemRedoLSN = 552977040335422",
                null,
                null,
                null,
                null,
                List.of(),
                Map.of()
        ));

        accumulator.enrichAndRecord(thirdRaw, new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                3,
                3,
                3,
                "sample.log",
                RUNTIME,
                ParseStatus.PARSED,
                "java_log_event",
                "2026-04-13 10:16:31,500",
                "WARN",
                "com.acme.orders.InventoryService",
                "main",
                "Order 456 delayed for request 123e4567-e89b-12d3-a456-426614174000",
                "2026-04-13 10:16:31,500 WARN [main] com.acme.InventoryService - Order 456 delayed",
                "Order 456 delayed for request 123e4567-e89b-12d3-a456-426614174000",
                null,
                null,
                null,
                null,
                List.of(),
                Map.of()
        ));

        var summary = accumulator.toSummary("legacy-java-parser", RUNTIME);
        var packageSummary = summary.topSignatures().getFirst();

        assertNotNull(enrichedSecond.signatureHash());
        assertEquals(60_877L, enrichedSecond.gapFromPreviousMs());
        assertEquals(1, summary.topSignatures().size());
        assertEquals("com.acme.orders", packageSummary.packageName());
        assertEquals(3L, packageSummary.count());
        assertEquals(1L, packageSummary.exceptionCount());
        assertEquals(1L, packageSummary.largeGapCount());
        assertEquals(1L, packageSummary.levelCounts().get("ERROR"));
        assertEquals(1L, packageSummary.levelCounts().get("INFO"));
        assertEquals(1L, packageSummary.levelCounts().get("WARN"));
        assertEquals(3, packageSummary.sampleMessages().size());
        assertEquals(3, packageSummary.sampleEvents().size());
        assertEquals(1, packageSummary.gapHighlights().size());
        assertEquals(1L, packageSummary.gapHighlights().getFirst().occurrenceCount());
        assertEquals("2026-04-13 10:15:30,123", packageSummary.gapHighlights().getFirst().previousEvent().timestamp());
        assertEquals("2026-04-13 10:16:31,000", packageSummary.gapHighlights().getFirst().currentEvent().timestamp());
        assertEquals("2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Order 123 failed",
                packageSummary.gapHighlights().getFirst().previousEvent().statement());
        assertEquals("2026-04-13 10:16:31,000 INFO [main] com.acme.OrderService - Started redo recovery",
                packageSummary.gapHighlights().getFirst().currentEvent().statement());
        assertEquals(true, packageSummary.sampleEvents().stream()
                .anyMatch(item -> item.stackSummary() != null
                        && item.stackSummary().startsWith("java.lang.IllegalStateException")));
        assertEquals(1L, summary.gapStatistics().buckets().get("60s+"));
        assertEquals(1L, summary.gapStatistics().buckets().get("100ms-1s"));
        assertEquals(1L, summary.topExceptions().getFirst().count());
    }

    @Test
    void respectsConfiguredLargeGapThreshold() {
        AnalysisSummaryAccumulator accumulator = new AnalysisSummaryAccumulator(120_000L);

        ReconstructedLogEvent firstRaw = new ReconstructedLogEvent("sample.log", 1, 1, 1, "first");
        ReconstructedLogEvent secondRaw = new ReconstructedLogEvent("sample.log", 2, 2, 2, "second");

        accumulator.enrichAndRecord(firstRaw, new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                1,
                1,
                "sample.log",
                RUNTIME,
                ParseStatus.PARSED,
                "java_log_event",
                "2026-04-13 10:15:30,123",
                "INFO",
                "com.acme.orders.OrderService",
                "main",
                "Started processing order 123",
                "2026-04-13 10:15:30,123 INFO [main] com.acme.orders.OrderService - Started processing order 123",
                "Started processing order 123",
                null,
                null,
                null,
                null,
                List.of(),
                Map.of()
        ));

        accumulator.enrichAndRecord(secondRaw, new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                2,
                2,
                2,
                "sample.log",
                RUNTIME,
                ParseStatus.PARSED,
                "java_log_event",
                "2026-04-13 10:16:31,000",
                "INFO",
                "com.acme.orders.OrderService",
                "main",
                "Finished processing order 123",
                "2026-04-13 10:16:31,000 INFO [main] com.acme.orders.OrderService - Finished processing order 123",
                "Finished processing order 123",
                null,
                null,
                null,
                null,
                List.of(),
                Map.of()
        ));

        var summary = accumulator.toSummary("legacy-java-parser", RUNTIME);

        assertEquals(0L, summary.topSignatures().getFirst().largeGapCount());
        assertEquals(0, summary.topSignatures().getFirst().gapHighlights().size());
    }

    @Test
    void collapsesGapHighlightsForRepeatedMessagesThatOnlyDifferInIdsAndLineNumbers() {
        AnalysisSummaryAccumulator accumulator = new AnalysisSummaryAccumulator(60_000L);

        accumulator.enrichAndRecord(
                new ReconstructedLogEvent("sample.log", 1, 1, 1, "first"),
                new LogEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        1,
                        1,
                        1,
                        "sample.log",
                        RUNTIME,
                        ParseStatus.PARSED,
                        "java_log_event",
                        "2026-04-13 10:00:00,000",
                        "ERROR",
                        "com.acme.orders.OrderService",
                        "main",
                        "Runtime error while performing subquery",
                        "2026-04-13 10:00:00,000 ERROR [main] com.acme.orders.OrderService - Runtime error while performing subquery",
                        "Runtime error while performing subquery",
                        null,
                        null,
                        "com.acme.XhiveInterruptedException",
                        null,
                        List.of(),
                        Map.of()
                ));

        accumulator.enrichAndRecord(
                new ReconstructedLogEvent("sample.log", 2, 2, 2, "second"),
                new LogEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        2,
                        2,
                        2,
                        "sample.log",
                        RUNTIME,
                        ParseStatus.PARSED,
                        "java_log_event",
                        "2026-04-13 10:05:00,000",
                        "INFO",
                        "com.acme.orders.OrderService",
                        "main",
                        "Started redo recovery: checkpointLSN = 552921208491179, systemRedoLSN = 552916920433265",
                        "2026-04-13 10:05:00,000 INFO [main] com.acme.orders.OrderService - Started redo recovery: checkpointLSN = 552921208491179, systemRedoLSN = 552916920433265",
                        "Started redo recovery: checkpointLSN = 552921208491179, systemRedoLSN = 552916920433265",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        Map.of()
                ));

        accumulator.enrichAndRecord(
                new ReconstructedLogEvent("sample.log", 3, 3, 3, "third"),
                new LogEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        3,
                        3,
                        3,
                        "sample.log",
                        RUNTIME,
                        ParseStatus.PARSED,
                        "java_log_event",
                        "2026-04-13 10:06:00,000",
                        "ERROR",
                        "com.acme.orders.OrderService",
                        "main",
                        "Runtime error while performing subquery",
                        "2026-04-13 10:06:00,000 ERROR [main] com.acme.orders.OrderService - Runtime error while performing subquery",
                        "Runtime error while performing subquery",
                        null,
                        null,
                        "com.acme.XhiveInterruptedException",
                        null,
                        List.of(),
                        Map.of()
                ));

        accumulator.enrichAndRecord(
                new ReconstructedLogEvent("sample.log", 4, 4, 4, "fourth"),
                new LogEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        4,
                        4,
                        4,
                        "sample.log",
                        RUNTIME,
                        ParseStatus.PARSED,
                        "java_log_event",
                        "2026-04-13 10:11:00,000",
                        "INFO",
                        "com.acme.orders.OrderService",
                        "main",
                        "Started redo recovery: checkpointLSN = 552977040341332, systemRedoLSN = 552977040335422",
                        "2026-04-13 10:11:00,000 INFO [main] com.acme.orders.OrderService - Started redo recovery: checkpointLSN = 552977040341332, systemRedoLSN = 552977040335422",
                        "Started redo recovery: checkpointLSN = 552977040341332, systemRedoLSN = 552977040335422",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        Map.of()
                ));

        var summary = accumulator.toSummary("legacy-java-parser", RUNTIME);
        var packageSummary = summary.topSignatures().getFirst();

        assertEquals(2L, packageSummary.largeGapCount());
        assertEquals(1, packageSummary.gapHighlights().size());
        assertEquals(2L, packageSummary.gapHighlights().getFirst().occurrenceCount());
    }
}
