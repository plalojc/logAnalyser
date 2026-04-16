package com.caseroot.loganalyser.export.parquet;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.StackFrame;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParquetArtifactExporterImplTest {

    @TempDir
    Path tempDir;

    @Test
    void writesParquetMagicBytes() throws Exception {
        Path ndjsonGz = tempDir.resolve("events.ndjson.gz");
        Path parquet = tempDir.resolve("events.parquet");
        Files.write(ndjsonGz, gzipSingleEvent());

        var exporter = new ParquetArtifactExporterImpl();
        exporter.export(
                new ArtifactDescriptor("parsed-events", ndjsonGz.toString(), "application/gzip", Instant.now()),
                new ArtifactDescriptor("parquet-events", parquet.toString(), "application/octet-stream", Instant.now())
        );

        byte[] bytes = Files.readAllBytes(parquet);
        assertTrue(bytes.length > 8);
        assertArrayEquals(new byte[]{'P', 'A', 'R', '1'}, Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(new byte[]{'P', 'A', 'R', '1'}, Arrays.copyOfRange(bytes, bytes.length - 4, bytes.length));
    }

    private byte[] gzipSingleEvent() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        LogEvent event = new LogEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                1L,
                3L,
                "server.log",
                new RuntimeDescriptor("java", "logback", "legacy-java", "1"),
                ParseStatus.PARSED,
                "exception",
                "2026-04-15T09:00:00Z",
                "ERROR",
                "com.example.DemoService",
                "main",
                "Failure while serving request",
                """
                        2026-04-15 14:30:00,000 ERROR com.example.DemoService - Failure while serving request
                        java.lang.IllegalStateException: bad state
                        \tat com.example.DemoService.run(DemoService.java:42)
                        """.strip(),
                "failure while serving request",
                "sig-1",
                25L,
                "java.lang.IllegalStateException",
                "java.lang.IllegalStateException",
                List.of(new StackFrame("com.example.DemoService", "run", "DemoService.java", 42)),
                Map.of("requestId", "abc-123")
        );

        byte[] ndjson = (objectMapper.writeValueAsString(event) + System.lineSeparator()).getBytes();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(buffer)) {
            gzipOutputStream.write(ndjson);
        }
        return buffer.toByteArray();
    }
}
