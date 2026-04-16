package com.caseroot.loganalyser.parser.polyglot;

import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotNetJsonParserPluginTest {

    @Test
    void parsesStructuredDotNetJsonEvent() {
        var plugin = new DotNetJsonParserPlugin();
        var event = plugin.parse(UUID.randomUUID(), new ReconstructedLogEvent(
                "api.json.log",
                1,
                1,
                1,
                "{\"@t\":\"2026-04-13T10:15:30.123Z\",\"@l\":\"Error\",\"@m\":\"Failed request 123\",\"SourceContext\":\"Order.Api\",\"Exception\":\"System.InvalidOperationException: Boom\"}"
        ));

        assertEquals(ParseStatus.PARSED, event.parseStatus());
        assertEquals("dotnet", event.runtime().family());
        assertEquals("ERROR", event.level());
        assertEquals("Order.Api", event.logger());
        assertEquals("System.InvalidOperationException", event.exceptionClass());
    }
}
