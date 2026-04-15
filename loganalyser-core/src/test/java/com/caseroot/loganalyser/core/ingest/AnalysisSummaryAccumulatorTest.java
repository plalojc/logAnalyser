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
    void groupsNormalizedMessagesIntoOneSignatureAndCapturesGapMetrics() {
        AnalysisSummaryAccumulator accumulator = new AnalysisSummaryAccumulator();

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
                "ERROR",
                "com.acme.OrderService",
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
                "2026-04-13 10:15:31,000",
                "ERROR",
                "com.acme.OrderService",
                "main",
                "Order 456 failed for request 123e4567-e89b-12d3-a456-426614174000",
                "2026-04-13 10:15:31,000 ERROR [main] com.acme.OrderService - Order 456 failed",
                "Order 456 failed for request 123e4567-e89b-12d3-a456-426614174000",
                null,
                null,
                "java.lang.IllegalStateException",
                "java.sql.SQLException",
                List.of(),
                Map.of()
        ));

        var summary = accumulator.toSummary("legacy-java-parser", RUNTIME);

        assertNotNull(enrichedSecond.signatureHash());
        assertEquals(877L, enrichedSecond.gapFromPreviousMs());
        assertEquals(2L, summary.topSignatures().getFirst().count());
        assertEquals("Order <NUM> failed for request <UUID>", summary.topSignatures().getFirst().normalizedMessage());
        assertEquals(1L, summary.gapStatistics().buckets().get("100ms-1s"));
        assertEquals(2L, summary.topExceptions().getFirst().count());
    }
}
