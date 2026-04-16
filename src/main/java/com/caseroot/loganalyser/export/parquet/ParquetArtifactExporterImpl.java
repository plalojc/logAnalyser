package com.caseroot.loganalyser.export.parquet;

import com.caseroot.loganalyser.domain.model.ArtifactDescriptor;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.spi.ParquetArtifactExporter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.io.OutputFile;
import org.apache.parquet.io.PositionOutputStream;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

public final class ParquetArtifactExporterImpl implements ParquetArtifactExporter {

    private static final Schema SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "LogEventRecord",
              "namespace": "com.caseroot.loganalyser.export.parquet",
              "fields": [
                {"name":"eventId","type":"string"},
                {"name":"jobId","type":"string"},
                {"name":"sequence","type":"long"},
                {"name":"lineStart","type":"long"},
                {"name":"lineEnd","type":"long"},
                {"name":"sourceFile","type":["null","string"],"default":null},
                {"name":"runtimeFamily","type":["null","string"],"default":null},
                {"name":"runtimeFramework","type":["null","string"],"default":null},
                {"name":"runtimeProfile","type":["null","string"],"default":null},
                {"name":"runtimeProfileVersion","type":["null","string"],"default":null},
                {"name":"parseStatus","type":["null","string"],"default":null},
                {"name":"classification","type":["null","string"],"default":null},
                {"name":"timestamp","type":["null","string"],"default":null},
                {"name":"level","type":["null","string"],"default":null},
                {"name":"logger","type":["null","string"],"default":null},
                {"name":"thread","type":["null","string"],"default":null},
                {"name":"message","type":["null","string"],"default":null},
                {"name":"rawEvent","type":["null","string"],"default":null},
                {"name":"normalizedMessage","type":["null","string"],"default":null},
                {"name":"signatureHash","type":["null","string"],"default":null},
                {"name":"gapFromPreviousMs","type":["null","long"],"default":null},
                {"name":"exceptionClass","type":["null","string"],"default":null},
                {"name":"rootCauseClass","type":["null","string"],"default":null},
                {"name":"stackFrameCount","type":"int"},
                {"name":"stackFramesJson","type":["null","string"],"default":null},
                {"name":"keyValuesJson","type":["null","string"],"default":null}
              ]
            }
            """);

    private final ObjectMapper objectMapper;

    public ParquetArtifactExporterImpl() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public String exporterId() {
        return "parquet-artifact-exporter";
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public void export(ArtifactDescriptor parsedArtifact, ArtifactDescriptor parquetArtifact) {
        java.nio.file.Path sourcePath = java.nio.file.Path.of(parsedArtifact.location());
        java.nio.file.Path targetPath = java.nio.file.Path.of(parquetArtifact.location());

        try {
            Files.createDirectories(targetPath.getParent());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(Files.newInputStream(sourcePath)),
                    StandardCharsets.UTF_8
            ));
                 var writer = AvroParquetWriter.<GenericRecord>builder(new NioOutputFile(targetPath))
                         .withSchema(SCHEMA)
                         .withCompressionCodec(CompressionCodecName.SNAPPY)
                         .build()) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LogEvent event = objectMapper.readValue(line, LogEvent.class);
                    writer.write(toRecord(event));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to export Parquet artifact to " + targetPath, exception);
        }
    }

    private GenericRecord toRecord(LogEvent event) throws IOException {
        GenericRecord record = new GenericData.Record(SCHEMA);
        record.put("eventId", event.eventId().toString());
        record.put("jobId", event.jobId().toString());
        record.put("sequence", event.sequence());
        record.put("lineStart", event.lineStart());
        record.put("lineEnd", event.lineEnd());
        record.put("sourceFile", event.sourceFile());
        record.put("runtimeFamily", event.runtime().family());
        record.put("runtimeFramework", event.runtime().framework());
        record.put("runtimeProfile", event.runtime().profile());
        record.put("runtimeProfileVersion", event.runtime().profileVersion());
        record.put("parseStatus", event.parseStatus().name());
        record.put("classification", event.classification());
        record.put("timestamp", event.timestamp());
        record.put("level", event.level());
        record.put("logger", event.logger());
        record.put("thread", event.thread());
        record.put("message", event.message());
        record.put("rawEvent", event.rawEvent());
        record.put("normalizedMessage", event.normalizedMessage());
        record.put("signatureHash", event.signatureHash());
        record.put("gapFromPreviousMs", event.gapFromPreviousMs());
        record.put("exceptionClass", event.exceptionClass());
        record.put("rootCauseClass", event.rootCauseClass());
        record.put("stackFrameCount", event.stackFrames().size());
        record.put("stackFramesJson", objectMapper.writeValueAsString(event.stackFrames()));
        record.put("keyValuesJson", objectMapper.writeValueAsString(event.keyValues()));
        return record;
    }

    private static final class NioOutputFile implements OutputFile {

        private final java.nio.file.Path path;

        private NioOutputFile(java.nio.file.Path path) {
            this.path = path;
        }

        @Override
        public PositionOutputStream create(long blockSizeHint) throws IOException {
            return createStream(true);
        }

        @Override
        public PositionOutputStream createOrOverwrite(long blockSizeHint) throws IOException {
            return createStream(false);
        }

        @Override
        public boolean supportsBlockSize() {
            return false;
        }

        @Override
        public long defaultBlockSize() {
            return 0L;
        }

        private PositionOutputStream createStream(boolean failIfExists) throws IOException {
            OutputStream outputStream = failIfExists
                    ? Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
                    : Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            return new NioPositionOutputStream(outputStream);
        }
    }

    private static final class NioPositionOutputStream extends PositionOutputStream {

        private final OutputStream delegate;
        private long position;

        private NioPositionOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public long getPos() {
            return position;
        }

        @Override
        public void write(int value) throws IOException {
            delegate.write(value);
            position++;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            position += length;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
