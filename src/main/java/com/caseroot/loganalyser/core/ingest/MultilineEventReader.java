package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class MultilineEventReader {

    private static final Pattern START_TIMESTAMP_PATTERN = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}.*|####<.*|\\[[0-9]{1,2}/[0-9]{1,2}/[0-9]{2,4} .*|[A-Z][a-z]{2} \\d{1,2}, \\d{4} .*|\\{.*|(?:TRACE|DEBUG|INFO|WARN|ERROR|FATAL|SEVERE|WARNING|CONFIG|FINE|FINER|FINEST)\\b.*)$"
    );
    private static final Pattern CONTINUATION_PATTERN = Pattern.compile(
            "^(\\s+at\\s+.*|Caused by:.*|Suppressed:.*|\\.\\.\\. \\d+ more|Traceback \\(most recent call last\\):|\\s+.*)$"
    );

    public void read(Path path, Consumer<ReconstructedLogEvent> consumer) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder currentEvent = new StringBuilder();
            long currentStartLine = 0;
            long currentEndLine = 0;
            long lineNumber = 0;
            long sequence = 0;
            String line;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                if (currentEvent.isEmpty()) {
                    currentStartLine = lineNumber;
                    currentEndLine = lineNumber;
                    currentEvent.append(line);
                    continue;
                }

                if (startsNewEvent(line)) {
                    consumer.accept(new ReconstructedLogEvent(
                            path.getFileName().toString(),
                            ++sequence,
                            currentStartLine,
                            currentEndLine,
                            currentEvent.toString()
                    ));

                    currentEvent.setLength(0);
                    currentEvent.append(line);
                    currentStartLine = lineNumber;
                    currentEndLine = lineNumber;
                    continue;
                }

                currentEvent.append(System.lineSeparator()).append(line);
                currentEndLine = lineNumber;
            }

            if (!currentEvent.isEmpty()) {
                consumer.accept(new ReconstructedLogEvent(
                        path.getFileName().toString(),
                        ++sequence,
                        currentStartLine,
                        currentEndLine,
                        currentEvent.toString()
                ));
            }
        }
    }

    private boolean startsNewEvent(String line) {
        return START_TIMESTAMP_PATTERN.matcher(line).matches() && !CONTINUATION_PATTERN.matcher(line).matches();
    }
}
