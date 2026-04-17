package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MultilineEventReaderTest {

    private static final byte[] BINARY_PREFIX = new byte[] {
            (byte) 0x1f, (byte) 0x8b, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03,
            (byte) 0xed, (byte) 0xc1, 0x01, 0x0d, 0x00, 0x00, 0x00, (byte) 0xc2, (byte) 0xa0, (byte) 0xf7,
            0x4f, 0x6d, 0x0e, 0x37, (byte) 0xa0, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, (byte) 0x80, 0x37, 0x03, (byte) 0x9a, (byte) 0xde, 0x1d, 0x27,
            0x00, 0x28, 0x00, 0x00, 0x00
    };

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

    @Test
    void readsGzipCompressedLogWithLogExtension() throws IOException {
        Path logFile = tempDir.resolve("compressed.log");
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(logFile))) {
            outputStream.write("""
                    2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed to place order
                    java.lang.IllegalStateException: Boom
                        at com.acme.OrderService.placeOrder(OrderService.java:42)
                    2026-04-13 10:15:31,000 INFO [main] com.acme.OrderService - Recovered
                    """.getBytes(StandardCharsets.UTF_8));
        }

        List<ReconstructedLogEvent> events = new ArrayList<>();
        new MultilineEventReader().read(logFile, events::add);

        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).lineStart());
        assertEquals(3L, events.get(0).lineEnd());
        assertEquals(4L, events.get(1).lineStart());
    }

    @Test
    void readsPlainTextLogAfterBinaryPrefixThatLooksLikeGzip() throws IOException {
        Path logFile = tempDir.resolve("prefixed.log");
        byte[] textBytes = """
                2026-04-13 10:15:30,123 ERROR [main] com.acme.OrderService - Failed to place order
                java.lang.IllegalStateException: Boom
                    at com.acme.OrderService.placeOrder(OrderService.java:42)
                2026-04-13 10:15:31,000 INFO [main] com.acme.OrderService - Recovered
                """.getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = Files.newOutputStream(logFile)) {
            outputStream.write(BINARY_PREFIX);
            outputStream.write(textBytes);
        }

        List<ReconstructedLogEvent> events = new ArrayList<>();
        new MultilineEventReader().read(logFile, events::add);

        assertEquals(2, events.size());
        assertEquals(1L, events.get(0).lineStart());
        assertEquals(3L, events.get(0).lineEnd());
        assertEquals(4L, events.get(1).lineStart());
        assertEquals(4L, events.get(1).lineEnd());
    }
}
