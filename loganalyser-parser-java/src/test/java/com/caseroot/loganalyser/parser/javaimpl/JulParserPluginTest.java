package com.caseroot.loganalyser.parser.javaimpl;

import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JulParserPluginTest {

    @Test
    void parsesJulHeaderAndLevelMessage() {
        var plugin = new JulParserPlugin();
        var event = plugin.parse(UUID.randomUUID(), new ReconstructedLogEvent(
                "server.log",
                1,
                1,
                3,
                """
                Apr 13, 2026 10:15:30 AM com.acme.OrderService placeOrder
                SEVERE: Failed to place order
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                """
        ));

        assertEquals(ParseStatus.PARSED, event.parseStatus());
        assertEquals("ERROR", event.level());
        assertEquals("com.acme.OrderService", event.logger());
        assertEquals("server.log", event.sourceFile());
    }
}
