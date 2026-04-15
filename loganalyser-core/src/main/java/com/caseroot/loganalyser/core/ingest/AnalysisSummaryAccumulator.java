package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.AnalysisSummaryCounts;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapStatistics;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SignatureSummary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class AnalysisSummaryAccumulator {

    private static final Pattern UUID_PATTERN = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final Pattern DURATION_PATTERN = Pattern.compile("\\b\\d+\\s*ms\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern HEX_PATTERN = Pattern.compile("\\b(?:0x)?[0-9a-fA-F]{8,}\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    private final Map<String, Long> levelCounts = new HashMap<>();
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, SignatureAggregate> signatures = new HashMap<>();
    private final Map<String, Long> exceptionCounts = new HashMap<>();
    private final Map<String, Long> gapBuckets = new LinkedHashMap<>();

    private long totalInputLines;
    private long totalEvents;
    private long parsedEvents;
    private long partialEvents;
    private long unclassifiedEvents;
    private long multilineEvents;
    private long totalGaps;
    private long outOfOrderGaps;
    private long missingTimestampEvents;
    private long totalGapMs;
    private Long minGapMs;
    private Long maxGapMs;
    private Instant previousTimestamp;

    AnalysisSummaryAccumulator() {
        gapBuckets.put("0-100ms", 0L);
        gapBuckets.put("100ms-1s", 0L);
        gapBuckets.put("1s-10s", 0L);
        gapBuckets.put("10s-60s", 0L);
        gapBuckets.put("60s+", 0L);
    }

    LogEvent enrichAndRecord(ReconstructedLogEvent reconstructedLogEvent, LogEvent logEvent) {
        LogEvent normalizedEvent = normalize(logEvent);
        Long gap = calculateGap(normalizedEvent.timestamp());

        LogEvent enrichedEvent = new LogEvent(
                normalizedEvent.eventId(),
                normalizedEvent.jobId(),
                normalizedEvent.sequence(),
                normalizedEvent.lineStart(),
                normalizedEvent.lineEnd(),
                normalizedEvent.sourceFile(),
                normalizedEvent.runtime(),
                normalizedEvent.parseStatus(),
                normalizedEvent.classification(),
                normalizedEvent.timestamp(),
                normalizedEvent.level(),
                normalizedEvent.logger(),
                normalizedEvent.thread(),
                normalizedEvent.message(),
                normalizedEvent.rawEvent(),
                normalizedEvent.normalizedMessage(),
                normalizedEvent.signatureHash(),
                gap,
                normalizedEvent.exceptionClass(),
                normalizedEvent.rootCauseClass(),
                normalizedEvent.stackFrames(),
                normalizedEvent.keyValues()
        );

        totalInputLines += reconstructedLogEvent.lineEnd() - reconstructedLogEvent.lineStart() + 1;
        totalEvents++;

        if (reconstructedLogEvent.lineEnd() > reconstructedLogEvent.lineStart()) {
            multilineEvents++;
        }

        if (enrichedEvent.level() != null && !enrichedEvent.level().isBlank()) {
            levelCounts.merge(enrichedEvent.level(), 1L, Long::sum);
        }

        if (enrichedEvent.parseStatus() == ParseStatus.PARSED) {
            parsedEvents++;
        } else if (enrichedEvent.parseStatus() == ParseStatus.PARTIAL) {
            partialEvents++;
        } else {
            unclassifiedEvents++;
        }

        String exceptionKey = buildExceptionKey(enrichedEvent.exceptionClass(), enrichedEvent.rootCauseClass());
        if (exceptionKey != null) {
            exceptionCounts.merge(exceptionKey, 1L, Long::sum);
        }

        signatures.computeIfAbsent(enrichedEvent.signatureHash(), ignored -> SignatureAggregate.from(enrichedEvent))
                .record(enrichedEvent);

        return enrichedEvent;
    }

    void warning(String warning) {
        warnings.add(warning);
    }

    AnalysisSummary toSummary(String parserPluginId, RuntimeDescriptor runtimeDescriptor) {
        return new AnalysisSummary(
                parserPluginId,
                runtimeDescriptor,
                new AnalysisSummaryCounts(
                        totalInputLines,
                        totalEvents,
                        parsedEvents,
                        partialEvents,
                        unclassifiedEvents,
                        multilineEvents,
                        0
                ),
                levelCounts,
                new GapStatistics(
                        totalGaps,
                        minGapMs,
                        maxGapMs,
                        totalGaps == 0 ? null : (double) totalGapMs / totalGaps,
                        outOfOrderGaps,
                        missingTimestampEvents,
                        gapBuckets
                ),
                signatures.values().stream()
                        .sorted(Comparator.comparingLong(SignatureAggregate::count).reversed())
                        .map(SignatureAggregate::toSummary)
                        .limit(25)
                        .toList(),
                exceptionCounts.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .map(entry -> {
                            String[] parts = entry.getKey().split("\\|", 2);
                            String exceptionClass = parts[0].isBlank() ? null : parts[0];
                            String rootCauseClass = parts.length == 2 && !parts[1].isBlank() ? parts[1] : null;
                            return new ExceptionSummary(exceptionClass, rootCauseClass, entry.getValue());
                        })
                        .limit(25)
                        .toList(),
                warnings
        );
    }

    private LogEvent normalize(LogEvent logEvent) {
        String source = firstNonBlank(logEvent.message(), logEvent.rawEvent());
        String normalizedMessage = normalizeMessage(source);
        String signatureHash = hash("%s|%s|%s|%s".formatted(
                blankToEmpty(logEvent.level()),
                blankToEmpty(logEvent.logger()),
                blankToEmpty(logEvent.exceptionClass()),
                normalizedMessage
        ));

        return new LogEvent(
                logEvent.eventId(),
                logEvent.jobId(),
                logEvent.sequence(),
                logEvent.lineStart(),
                logEvent.lineEnd(),
                logEvent.sourceFile(),
                logEvent.runtime(),
                logEvent.parseStatus(),
                logEvent.classification(),
                logEvent.timestamp(),
                logEvent.level(),
                logEvent.logger(),
                logEvent.thread(),
                logEvent.message(),
                logEvent.rawEvent(),
                normalizedMessage,
                signatureHash,
                logEvent.gapFromPreviousMs(),
                logEvent.exceptionClass(),
                logEvent.rootCauseClass(),
                logEvent.stackFrames(),
                logEvent.keyValues()
        );
    }

    private Long calculateGap(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            missingTimestampEvents++;
            return null;
        }

        Instant current = TimestampSupport.parse(timestamp);
        if (current == null) {
            missingTimestampEvents++;
            return null;
        }

        if (previousTimestamp == null) {
            previousTimestamp = current;
            return null;
        }

        long gap = current.toEpochMilli() - previousTimestamp.toEpochMilli();
        previousTimestamp = current;
        totalGaps++;
        totalGapMs += Math.abs(gap);

        if (minGapMs == null || gap < minGapMs) {
            minGapMs = gap;
        }
        if (maxGapMs == null || gap > maxGapMs) {
            maxGapMs = gap;
        }
        if (gap < 0) {
            outOfOrderGaps++;
        }

        bucket(gap);
        return gap;
    }

    private void bucket(long gap) {
        long absolute = Math.abs(gap);
        String key;
        if (absolute <= 100) {
            key = "0-100ms";
        } else if (absolute <= 1_000) {
            key = "100ms-1s";
        } else if (absolute <= 10_000) {
            key = "1s-10s";
        } else if (absolute <= 60_000) {
            key = "10s-60s";
        } else {
            key = "60s+";
        }
        gapBuckets.merge(key, 1L, Long::sum);
    }

    private String normalizeMessage(String message) {
        String normalized = message == null ? "" : message;
        normalized = UUID_PATTERN.matcher(normalized).replaceAll("<UUID>");
        normalized = DURATION_PATTERN.matcher(normalized).replaceAll("<DURATION_MS>");
        normalized = IP_PATTERN.matcher(normalized).replaceAll("<IP>");
        normalized = HEX_PATTERN.matcher(normalized).replaceAll("<HEX>");
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("<NUM>");
        return normalized;
    }

    private String buildExceptionKey(String exceptionClass, String rootCauseClass) {
        if ((exceptionClass == null || exceptionClass.isBlank()) && (rootCauseClass == null || rootCauseClass.isBlank())) {
            return null;
        }
        return blankToEmpty(exceptionClass) + "|" + blankToEmpty(rootCauseClass);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < 8; index++) {
                builder.append(String.format("%02x", bytes[index]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private static final class SignatureAggregate {
        private final String signatureHash;
        private final String normalizedMessage;
        private final String level;
        private final String logger;
        private final String exceptionClass;
        private final String rootCauseClass;
        private String firstSeenTimestamp;
        private String lastSeenTimestamp;
        private long count;

        private SignatureAggregate(
                String signatureHash,
                String normalizedMessage,
                String level,
                String logger,
                String exceptionClass,
                String rootCauseClass,
                String firstSeenTimestamp,
                String lastSeenTimestamp,
                long count
        ) {
            this.signatureHash = signatureHash;
            this.normalizedMessage = normalizedMessage;
            this.level = level;
            this.logger = logger;
            this.exceptionClass = exceptionClass;
            this.rootCauseClass = rootCauseClass;
            this.firstSeenTimestamp = firstSeenTimestamp;
            this.lastSeenTimestamp = lastSeenTimestamp;
            this.count = count;
        }

        static SignatureAggregate from(LogEvent event) {
            return new SignatureAggregate(
                    event.signatureHash(),
                    event.normalizedMessage(),
                    event.level(),
                    event.logger(),
                    event.exceptionClass(),
                    event.rootCauseClass(),
                    event.timestamp(),
                    event.timestamp(),
                    0
            );
        }

        void record(LogEvent event) {
            count++;
            if (firstSeenTimestamp == null) {
                firstSeenTimestamp = event.timestamp();
            }
            if (event.timestamp() != null) {
                lastSeenTimestamp = event.timestamp();
            }
        }

        long count() {
            return count;
        }

        SignatureSummary toSummary() {
            return new SignatureSummary(
                    signatureHash,
                    normalizedMessage,
                    level,
                    logger,
                    exceptionClass,
                    rootCauseClass,
                    firstSeenTimestamp,
                    lastSeenTimestamp,
                    count
            );
        }
    }
}
