package com.caseroot.loganalyser.parser.polyglot;

import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonLoggingParserPluginTest {

    @Test
    void parsesPythonTracebackEvent() {
        var plugin = new PythonLoggingParserPlugin();
        var event = plugin.parse(UUID.randomUUID(), new ReconstructedLogEvent(
                "worker.log",
                1,
                1,
                4,
                """
                2026-04-13 10:15:30,123 ERROR order.worker - Failed to process order
                Traceback (most recent call last):
                  File "/srv/app/worker.py", line 42, in run
                ValueError: bad order
                """
        ));

        assertEquals(ParseStatus.PARSED, event.parseStatus());
        assertEquals("python", event.runtime().family());
        assertEquals("ERROR", event.level());
        assertEquals("ValueError", event.exceptionClass());
        assertEquals("worker.log", event.sourceFile());
    }
}
