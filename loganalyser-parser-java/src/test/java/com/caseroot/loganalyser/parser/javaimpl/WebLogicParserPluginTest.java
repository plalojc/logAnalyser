package com.caseroot.loganalyser.parser.javaimpl;

import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebLogicParserPluginTest {

    @Test
    void parsesWebLogicFormattedLine() {
        var plugin = new WebLogicParserPlugin();
        var event = plugin.parse(UUID.randomUUID(), new ReconstructedLogEvent(
                "AdminServer.log",
                1,
                1,
                1,
                "####<Apr 13, 2026 10:15:30,123 AM IST> <Error> <OrderSubsystem> <host1> <AdminServer> <main> <anonymous> <> <diag> <BEA-000001> <Failed to process order 101>"
        ));

        assertEquals(ParseStatus.PARSED, event.parseStatus());
        assertEquals("ERROR", event.level());
        assertEquals("OrderSubsystem", event.logger());
        assertEquals("AdminServer.log", event.sourceFile());
    }
}
