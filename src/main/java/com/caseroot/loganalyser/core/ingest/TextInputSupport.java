package com.caseroot.loganalyser.core.ingest;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

final class TextInputSupport {

    private static final int SAMPLE_SIZE = 16 * 1024;
    private static final Pattern LOG_START_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}|####<|\\[[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4} |[A-Z][a-z]{2} \\d{1,2}, \\d{4} |\\{.*|(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL|SEVERE|WARNING|CONFIG|FINE|FINER|FINEST)\\b)"
    );

    private TextInputSupport() {
    }

    static BufferedReader openReader(Path path) throws IOException {
        TextSourceSelection selection = selectSource(path);
        InputStream inputStream = Files.newInputStream(path);
        BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);

        InputStream decodedInputStream = selection.gzipped()
                ? new GZIPInputStream(bufferedInputStream)
                : bufferedInputStream;

        skipFully(decodedInputStream, selection.skipBytes());

        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);

        return new BufferedReader(new InputStreamReader(decodedInputStream, decoder));
    }

    private static boolean isGzip(int first, int second) {
        return first == 0x1f && second == 0x8b;
    }

    private static TextSourceSelection selectSource(Path path) throws IOException {
        byte[] rawSample = readSample(Files.newInputStream(path));
        int rawOffset = findLikelyLogStart(rawSample);

        if (rawSample.length < 2 || !isGzip(rawSample[0] & 0xff, rawSample[1] & 0xff)) {
            return new TextSourceSelection(false, Math.max(0, rawOffset));
        }

        byte[] gzipSample = readGzipSample(path);
        int gzipOffset = findLikelyLogStart(gzipSample);

        if (rawOffset >= 0 && gzipOffset < 0) {
            return new TextSourceSelection(false, rawOffset);
        }
        if (gzipOffset >= 0 && rawOffset < 0) {
            return new TextSourceSelection(true, gzipOffset);
        }
        if (gzipOffset >= 0) {
            return new TextSourceSelection(true, gzipOffset);
        }
        if (rawOffset >= 0) {
            return new TextSourceSelection(false, rawOffset);
        }

        return new TextSourceSelection(isGzip(rawSample[0] & 0xff, rawSample[1] & 0xff), 0);
    }

    private static byte[] readGzipSample(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
             GZIPInputStream gzipInputStream = new GZIPInputStream(bufferedInputStream)) {
            return readSample(gzipInputStream);
        } catch (IOException exception) {
            return new byte[0];
        }
    }

    private static byte[] readSample(InputStream inputStream) throws IOException {
        try (InputStream closableInputStream = inputStream;
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int totalRead = 0;
            int read;
            while (totalRead < SAMPLE_SIZE
                    && (read = closableInputStream.read(buffer, 0, Math.min(buffer.length, SAMPLE_SIZE - totalRead))) > 0) {
                outputStream.write(buffer, 0, read);
                totalRead += read;
            }
            return outputStream.toByteArray();
        }
    }

    private static int findLikelyLogStart(byte[] sample) {
        if (sample.length == 0) {
            return -1;
        }

        String probe = new String(sample, StandardCharsets.ISO_8859_1);
        Matcher matcher = LOG_START_PATTERN.matcher(probe);
        while (matcher.find()) {
            int start = matcher.start(1);
            if (isMostlyPrintable(sample, start)) {
                return start;
            }
        }
        return -1;
    }

    private static boolean isMostlyPrintable(byte[] sample, int offset) {
        int end = Math.min(sample.length, offset + 256);
        if (offset >= end) {
            return false;
        }

        int printable = 0;
        int total = 0;
        for (int index = offset; index < end; index++) {
            int value = sample[index] & 0xff;
            total++;
            if (value == '\r' || value == '\n' || value == '\t' || (value >= 32 && value <= 126)) {
                printable++;
            }
        }
        return printable * 100 >= total * 85;
    }

    private static void skipFully(InputStream inputStream, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            long skipped = inputStream.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }

            if (inputStream.read() == -1) {
                break;
            }
            remaining--;
        }
    }

    private record TextSourceSelection(boolean gzipped, int skipBytes) {
    }
}
