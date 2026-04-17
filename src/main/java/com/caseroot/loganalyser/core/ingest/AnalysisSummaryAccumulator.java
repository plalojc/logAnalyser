package com.caseroot.loganalyser.core.ingest;

import com.caseroot.loganalyser.domain.model.AnalysisFocus;
import com.caseroot.loganalyser.domain.model.AnalysisOptions;
import com.caseroot.loganalyser.domain.model.AnalysisSummary;
import com.caseroot.loganalyser.domain.model.AnalysisSummaryCounts;
import com.caseroot.loganalyser.domain.model.EventSnippet;
import com.caseroot.loganalyser.domain.model.ExceptionSummary;
import com.caseroot.loganalyser.domain.model.GapHighlight;
import com.caseroot.loganalyser.domain.model.GapStatistics;
import com.caseroot.loganalyser.domain.model.LogEvent;
import com.caseroot.loganalyser.domain.model.ParseStatus;
import com.caseroot.loganalyser.domain.model.ReconstructedLogEvent;
import com.caseroot.loganalyser.domain.model.RuntimeDescriptor;
import com.caseroot.loganalyser.domain.model.SignatureSummary;
import com.caseroot.loganalyser.domain.model.StackFrame;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class AnalysisSummaryAccumulator {

    private static final int SAMPLE_MESSAGE_LIMIT = 3;
    private static final int SAMPLE_EVENT_LIMIT = 3;
    private static final int GAP_HIGHLIGHT_LIMIT = 3;
    private static final int HIGHLIGHT_LIMIT = 5;
    private static final int MAX_STATEMENT_LENGTH = 240;
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
    private long focusedEvents;
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
    private EventSnippet previousFocusedEventSnippet;
    private final long largeGapHighlightThresholdMs;
    private final AnalysisOptions analysisOptions;
    private final Set<AnalysisFocus> focusSelections;

    AnalysisSummaryAccumulator() {
        this(60_000L, new AnalysisOptions(null, List.of(AnalysisFocus.ALL)));
    }

    AnalysisSummaryAccumulator(long largeGapHighlightThresholdMs) {
        this(largeGapHighlightThresholdMs, new AnalysisOptions(null, List.of(AnalysisFocus.ALL)));
    }

    AnalysisSummaryAccumulator(long largeGapHighlightThresholdMs, AnalysisOptions analysisOptions) {
        this.largeGapHighlightThresholdMs = largeGapHighlightThresholdMs;
        this.analysisOptions = analysisOptions == null
                ? new AnalysisOptions(null, List.of(AnalysisFocus.ALL))
                : analysisOptions;
        this.focusSelections = new LinkedHashSet<>(this.analysisOptions.focusSelections());
        gapBuckets.put("0-100ms", 0L);
        gapBuckets.put("100ms-1s", 0L);
        gapBuckets.put("1s-10s", 0L);
        gapBuckets.put("10s-60s", 0L);
        gapBuckets.put("60s+", 0L);
    }

    LogEvent enrichAndRecord(ReconstructedLogEvent reconstructedLogEvent, LogEvent logEvent) {
        LogEvent normalizedEvent = normalize(logEvent);
        boolean focused = matchesFocus(normalizedEvent);
        Long gap = focused ? calculateGap(normalizedEvent.timestamp()) : null;

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

        if (enrichedEvent.parseStatus() == ParseStatus.PARSED) {
            parsedEvents++;
        } else if (enrichedEvent.parseStatus() == ParseStatus.PARTIAL) {
            partialEvents++;
        } else {
            unclassifiedEvents++;
        }

        if (focused) {
            focusedEvents++;
            GapHighlight gapHighlight = null;
            EventSnippet currentSnippet = toEventSnippet(enrichedEvent);
            if (gap != null && Math.abs(gap) > largeGapHighlightThresholdMs && previousFocusedEventSnippet != null) {
                gapHighlight = new GapHighlight(gap, 1L, previousFocusedEventSnippet, currentSnippet);
            }

            if (enrichedEvent.level() != null && !enrichedEvent.level().isBlank()) {
                levelCounts.merge(enrichedEvent.level(), 1L, Long::sum);
            }

            String exceptionKey = buildExceptionKey(enrichedEvent.exceptionClass(), enrichedEvent.rootCauseClass());
            if (exceptionKey != null) {
                exceptionCounts.merge(exceptionKey, 1L, Long::sum);
            }

            signatures.computeIfAbsent(
                            enrichedEvent.signatureHash(),
                            ignored -> SignatureAggregate.from(enrichedEvent, largeGapHighlightThresholdMs))
                    .record(enrichedEvent, currentSnippet, gapHighlight);
            previousFocusedEventSnippet = currentSnippet;
        }

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
                        focusedEvents,
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
                        .sorted(Comparator.comparingLong(SignatureAggregate::count).reversed()
                                .thenComparingLong(SignatureAggregate::exceptionCount).reversed()
                                .thenComparing(SignatureAggregate::packageName))
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
                buildWarnings()
        );
    }

    private List<String> buildWarnings() {
        List<String> allWarnings = new ArrayList<>(warnings);
        if (!focusSelections.contains(AnalysisFocus.ALL)) {
            allWarnings.add("Focused analysis applied: " + focusSelections + ". Non-matching events were preserved in parsed artifacts but excluded from summary grouping.");
        }
        return allWarnings;
    }

    private LogEvent normalize(LogEvent logEvent) {
        String source = firstNonBlank(logEvent.message(), logEvent.rawEvent());
        String normalizedMessage = normalizeMessage(source);
        String packageName = extractPackageName(logEvent);
        String signatureHash = hash(packageName);

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

    private static String buildExceptionKey(String exceptionClass, String rootCauseClass) {
        if ((exceptionClass == null || exceptionClass.isBlank()) && (rootCauseClass == null || rootCauseClass.isBlank())) {
            return null;
        }
        return blankToEmpty(exceptionClass) + "|" + blankToEmpty(rootCauseClass);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null ? "" : second;
    }

    private static String extractPackageName(LogEvent event) {
        String logger = firstNonBlank(event.logger(), null);
        if (!logger.isBlank()) {
            return toPackageName(logger);
        }
        if (!event.stackFrames().isEmpty()) {
            StackFrame firstFrame = event.stackFrames().getFirst();
            if (firstFrame.className() != null && !firstFrame.className().isBlank()) {
                return toPackageName(firstFrame.className());
            }
        }
        return "unscoped";
    }

    private static String toPackageName(String value) {
        String candidate = value.trim();
        int innerClassSeparator = candidate.indexOf('$');
        if (innerClassSeparator >= 0) {
            candidate = candidate.substring(0, innerClassSeparator);
        }
        int lastDot = candidate.lastIndexOf('.');
        if (lastDot < 0) {
            return candidate;
        }

        String lastSegment = candidate.substring(lastDot + 1);
        if (!lastSegment.isEmpty() && Character.isUpperCase(lastSegment.charAt(0))) {
            return candidate.substring(0, lastDot);
        }
        return candidate;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean matchesFocus(LogEvent event) {
        if (analysisOptions.includes(AnalysisFocus.ALL)) {
            return true;
        }

        if (analysisOptions.includes(AnalysisFocus.EXCEPTION)
                && ((event.exceptionClass() != null && !event.exceptionClass().isBlank())
                || (event.rootCauseClass() != null && !event.rootCauseClass().isBlank()))) {
            return true;
        }

        String level = blankToEmpty(event.level()).toUpperCase();
        if ((analysisOptions.includes(AnalysisFocus.ERROR) && ("ERROR".equals(level) || "FATAL".equals(level)))
                || (analysisOptions.includes(AnalysisFocus.WARN) && "WARN".equals(level))
                || (analysisOptions.includes(AnalysisFocus.INFO) && "INFO".equals(level))
                || (analysisOptions.includes(AnalysisFocus.DEBUG) && "DEBUG".equals(level))
                || (analysisOptions.includes(AnalysisFocus.TRACE) && "TRACE".equals(level))) {
            return true;
        }

        return false;
    }

    private EventSnippet toEventSnippet(LogEvent event) {
        return new EventSnippet(
                event.sourceFile(),
                event.timestamp(),
                event.level(),
                event.logger(),
                event.message(),
                summarizeStatement(event),
                event.exceptionClass(),
                event.rootCauseClass(),
                summarizeStack(event)
        );
    }

    private String summarizeStatement(LogEvent event) {
        String rawEvent = event.rawEvent();
        String statement = rawEvent == null || rawEvent.isBlank()
                ? "%s %s - %s".formatted(
                blankToEmpty(event.level()),
                blankToEmpty(event.logger()),
                firstNonBlank(event.message(), "<no-message>"))
                : rawEvent.lines().findFirst().orElse(rawEvent);
        return abbreviate(statement);
    }

    private String summarizeStack(LogEvent event) {
        String primaryException = firstNonBlank(event.exceptionClass(), event.rootCauseClass());
        if (primaryException.isBlank()) {
            return null;
        }
        if (event.stackFrames().isEmpty()) {
            return primaryException;
        }

        StackFrame topFrame = event.stackFrames().getFirst();
        String location = firstNonBlank(topFrame.className(), "<unknown>");
        if (topFrame.methodName() != null && !topFrame.methodName().isBlank()) {
            location += "." + topFrame.methodName();
        }
        if (topFrame.fileName() != null && !topFrame.fileName().isBlank()) {
            location += "(" + topFrame.fileName();
            if (topFrame.lineNumber() != null) {
                location += ":" + topFrame.lineNumber();
            }
            location += ")";
        }

        return primaryException + " at " + location + " [" + event.stackFrames().size() + " frames]";
    }

    private static String abbreviate(String value) {
        if (value == null || value.length() <= MAX_STATEMENT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_STATEMENT_LENGTH - 3) + "...";
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
        private final String packageName;
        private String representativeLogger;
        private String firstSeenTimestamp;
        private String lastSeenTimestamp;
        private final Map<String, Long> levelCounts = new LinkedHashMap<>();
        private final Map<String, Long> messageCounts = new LinkedHashMap<>();
        private final Map<String, Long> highlightCounts = new LinkedHashMap<>();
        private final Map<String, EventSnippet> sampleEventsByMessage = new LinkedHashMap<>();
        private final Map<String, GapHighlightAggregate> gapHighlightAggregates = new LinkedHashMap<>();
        private final long largeGapHighlightThresholdMs;
        private long count;
        private long exceptionCount;
        private long largeGapCount;

        private SignatureAggregate(
                String signatureHash,
                String packageName,
                String representativeLogger,
                String firstSeenTimestamp,
                String lastSeenTimestamp,
                long largeGapHighlightThresholdMs
        ) {
            this.signatureHash = signatureHash;
            this.packageName = packageName;
            this.representativeLogger = representativeLogger;
            this.firstSeenTimestamp = firstSeenTimestamp;
            this.lastSeenTimestamp = lastSeenTimestamp;
            this.largeGapHighlightThresholdMs = largeGapHighlightThresholdMs;
        }

        static SignatureAggregate from(LogEvent event, long largeGapHighlightThresholdMs) {
            return new SignatureAggregate(
                    event.signatureHash(),
                    extractPackageName(event),
                    event.logger(),
                    event.timestamp(),
                    event.timestamp(),
                    largeGapHighlightThresholdMs
            );
        }

        void record(LogEvent event, EventSnippet currentSnippet, GapHighlight gapHighlight) {
            count++;
            if ((representativeLogger == null || representativeLogger.isBlank())
                    && event.logger() != null && !event.logger().isBlank()) {
                representativeLogger = event.logger();
            }
            if (firstSeenTimestamp == null) {
                firstSeenTimestamp = event.timestamp();
            }
            if (event.timestamp() != null) {
                lastSeenTimestamp = event.timestamp();
            }
            if (event.level() != null && !event.level().isBlank()) {
                levelCounts.merge(event.level(), 1L, Long::sum);
            }
            if (event.normalizedMessage() != null && !event.normalizedMessage().isBlank()) {
                messageCounts.merge(event.normalizedMessage(), 1L, Long::sum);
                sampleEventsByMessage.putIfAbsent(event.normalizedMessage(), currentSnippet);
            }
            if (event.exceptionClass() != null && !event.exceptionClass().isBlank()) {
                exceptionCount++;
                highlightCounts.merge(formatExceptionHighlight(event), 1L, Long::sum);
            } else if (event.rootCauseClass() != null && !event.rootCauseClass().isBlank()) {
                exceptionCount++;
                highlightCounts.merge(formatExceptionHighlight(event), 1L, Long::sum);
            }
            if (event.level() != null && ("ERROR".equalsIgnoreCase(event.level()) || "FATAL".equalsIgnoreCase(event.level()))) {
                highlightCounts.merge("Error-level event: " + fallbackMessage(event.normalizedMessage()), 1L, Long::sum);
            }
            if (event.gapFromPreviousMs() != null && Math.abs(event.gapFromPreviousMs()) > largeGapHighlightThresholdMs) {
                largeGapCount++;
                highlightCounts.merge(formatGapHighlight(event), 1L, Long::sum);
                if (gapHighlight != null) {
                    String key = gapKey(gapHighlight);
                    gapHighlightAggregates.computeIfAbsent(key, ignored -> new GapHighlightAggregate(gapHighlight))
                            .record(gapHighlight.gapMs());
                }
            }
        }

        long count() {
            return count;
        }

        long exceptionCount() {
            return exceptionCount;
        }

        String packageName() {
            return packageName;
        }

        SignatureSummary toSummary() {
            return new SignatureSummary(
                    signatureHash,
                    packageName,
                    representativeLogger,
                    firstSeenTimestamp,
                    lastSeenTimestamp,
                    count,
                    levelCounts,
                    exceptionCount,
                    largeGapCount,
                    messageCounts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                    .thenComparing(Map.Entry::getKey))
                            .limit(SAMPLE_MESSAGE_LIMIT)
                            .map(Map.Entry::getKey)
                            .toList(),
                    messageCounts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                    .thenComparing(Map.Entry::getKey))
                            .map(Map.Entry::getKey)
                            .filter(sampleEventsByMessage::containsKey)
                            .limit(SAMPLE_EVENT_LIMIT)
                            .map(sampleEventsByMessage::get)
                            .toList(),
                    gapHighlightAggregates.values().stream()
                            .sorted(Comparator.comparingLong(GapHighlightAggregate::occurrenceCount).reversed()
                                    .thenComparingLong(GapHighlightAggregate::largestGapMs).reversed())
                            .limit(GAP_HIGHLIGHT_LIMIT)
                            .map(GapHighlightAggregate::toHighlight)
                            .toList(),
                    highlightCounts.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                                    .thenComparing(Map.Entry::getKey))
                            .limit(HIGHLIGHT_LIMIT)
                            .map(entry -> entry.getValue() > 1
                                    ? entry.getKey() + " (" + entry.getValue() + ")"
                                    : entry.getKey())
                            .toList()
            );
        }

        private String formatExceptionHighlight(LogEvent event) {
            String exceptionPart = firstNonBlank(event.exceptionClass(), event.rootCauseClass());
            String rootCausePart = event.rootCauseClass();
            if (rootCausePart != null && !rootCausePart.isBlank()
                    && !rootCausePart.equals(exceptionPart)) {
                return "Exception: " + exceptionPart + " -> " + rootCausePart;
            }
            return "Exception: " + exceptionPart;
        }

        private String formatGapHighlight(LogEvent event) {
            return "Large gap before " + abbreviate(fallbackMessage(event.normalizedMessage()));
        }

        private String fallbackMessage(String value) {
            return value == null || value.isBlank() ? "<no-message>" : value;
        }

        private String gapKey(GapHighlight gapHighlight) {
            return normalizeGapEventSignature(gapHighlight.previousEvent()) + "|" + normalizeGapEventSignature(gapHighlight.currentEvent());
        }

        private String normalizeGapEventSignature(EventSnippet eventSnippet) {
            return blankToEmpty(eventSnippet.level()) + "|"
                    + blankToEmpty(eventSnippet.logger()) + "|"
                    + normalizeGroupingText(eventSnippet.message()) + "|"
                    + blankToEmpty(eventSnippet.exceptionClass()) + "|"
                    + normalizeStackGrouping(eventSnippet.stackSummary());
        }

        private String normalizeGroupingText(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            String normalized = UUID_PATTERN.matcher(value).replaceAll("<UUID>");
            normalized = DURATION_PATTERN.matcher(normalized).replaceAll("<DURATION_MS>");
            normalized = IP_PATTERN.matcher(normalized).replaceAll("<IP>");
            normalized = HEX_PATTERN.matcher(normalized).replaceAll("<HEX>");
            normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("<NUM>");
            return normalized;
        }

        private String normalizeStackGrouping(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            String normalized = normalizeGroupingText(value);
            normalized = normalized.replaceAll("\\(.*?\\)", "(...)");
            normalized = normalized.replaceAll("\\s*\\[[^\\]]+\\]", "");
            return normalized;
        }

        private static final class GapHighlightAggregate {
            private final EventSnippet previousEvent;
            private final EventSnippet currentEvent;
            private long largestGapMs;
            private long occurrenceCount;

            private GapHighlightAggregate(GapHighlight initialGap) {
                this.previousEvent = initialGap.previousEvent();
                this.currentEvent = initialGap.currentEvent();
                this.largestGapMs = initialGap.gapMs();
                this.occurrenceCount = 0L;
            }

            void record(long gapMs) {
                occurrenceCount++;
                if (gapMs > largestGapMs) {
                    largestGapMs = gapMs;
                }
            }

            long largestGapMs() {
                return largestGapMs;
            }

            long occurrenceCount() {
                return occurrenceCount;
            }

            GapHighlight toHighlight() {
                return new GapHighlight(largestGapMs, occurrenceCount, previousEvent, currentEvent);
            }
        }
    }
}
