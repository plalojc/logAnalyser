package com.caseroot.loganalyser.parser.javaimpl;

import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LegacyJavaParserPluginTest {

    @Test
    void parsesLegacyJavaLogLineWithStackTraceMetadata() {
        var plugin = new LegacyJavaParserPlugin();
        var reconstructed = new ReconstructedLogEvent(
                "server.log",
                1,
                1,
                3,
                """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed to place order
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                """
        );

        var event = plugin.parse(UUID.randomUUID(), reconstructed);

        assertEquals(ParseStatus.PARSED, event.parseStatus());
        assertEquals("ERROR", event.level());
        assertEquals("com.acme.OrderService", event.logger());
        assertEquals("main", event.thread());
        assertEquals("Failed to place order", event.message());
        assertEquals("java.lang.IllegalStateException", event.exceptionClass());
        assertNotNull(event.stackFrames());
        assertEquals(1, event.stackFrames().size());
    }
}

