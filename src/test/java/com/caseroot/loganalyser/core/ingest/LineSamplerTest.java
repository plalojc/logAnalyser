package com.caseroot.loganalyser.core.ingest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineSamplerTest {

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
    void samplesGzipCompressedLogEvenWhenExtensionIsLog() throws IOException {
        Path logFile = tempDir.resolve("sample.log");
        try (OutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(logFile))) {
            outputStream.write("""
                    2026-04-07 16:19:03,532 DEBUG [main] com.acme.Service - first
                    2026-04-07 16:19:04,532 INFO [main] com.acme.Service - second
                    """.getBytes(StandardCharsets.UTF_8));
        }

        List<String> lines = new LineSampler().sample(logFile, 10);

        assertEquals(2, lines.size());
        assertEquals("2026-04-07 16:19:03,532 DEBUG [main] com.acme.Service - first", lines.getFirst());
        assertEquals("2026-04-07 16:19:04,532 INFO [main] com.acme.Service - second", lines.get(1));
    }

    @Test
    void samplesPlainTextLogAfterBinaryPrefixThatLooksLikeGzip() throws IOException {
        Path logFile = tempDir.resolve("prefixed.log");
        byte[] textBytes = """
                2026-04-07 16:19:03,532 DEBUG [main] com.acme.Service - first
                2026-04-07 16:19:04,532 INFO [main] com.acme.Service - second
                """.getBytes(StandardCharsets.UTF_8);

        try (OutputStream outputStream = Files.newOutputStream(logFile)) {
            outputStream.write(BINARY_PREFIX);
            outputStream.write(textBytes);
        }

        List<String> lines = new LineSampler().sample(logFile, 10);

        assertEquals(2, lines.size());
        assertEquals("2026-04-07 16:19:03,532 DEBUG [main] com.acme.Service - first", lines.getFirst());
        assertEquals("2026-04-07 16:19:04,532 INFO [main] com.acme.Service - second", lines.get(1));
    }
}
